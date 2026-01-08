package com.playprobie.api.domain.analytics.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.playprobie.api.domain.analytics.dao.QuestionResponseAnalysisRepository;
import com.playprobie.api.domain.analytics.domain.QuestionResponseAnalysis;
import com.playprobie.api.domain.analytics.dto.QuestionResponseAnalysisWrapper;
import com.playprobie.api.domain.interview.dao.InterviewLogRepository;
import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.survey.dao.FixedQuestionRepository;
import com.playprobie.api.domain.survey.domain.FixedQuestion;
import com.playprobie.api.infra.ai.AiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

	private final InterviewLogRepository interviewLogRepository;
	private final QuestionResponseAnalysisRepository questionResponseAnalysisRepository;
	private final AiClient aiClient;
	private final FixedQuestionRepository fixedQuestionRepository;
	private final com.playprobie.api.domain.survey.dao.SurveyRepository surveyRepository;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;

	// 동시성 제어: 동일 surveyId에 대한 중복 분석 방지
	private final ConcurrentHashMap<Long, Boolean> analysisInProgress = new ConcurrentHashMap<>();

	/**
	 * 설문 전체 질문 분석 결과 조회 (캐시 or AI 분석)
	 */
	public Flux<QuestionResponseAnalysisWrapper> getSurveyAnalysis(UUID surveyUuid) {
		log.info("🔍 분석 요청: surveyUuid={}", surveyUuid);

		com.playprobie.api.domain.survey.domain.Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(() -> new com.playprobie.api.global.error.exception.BusinessException(
				com.playprobie.api.global.error.ErrorCode.ENTITY_NOT_FOUND));
		Long surveyId = survey.getId();

		List<FixedQuestion> questions = fixedQuestionRepository.findBySurveyIdOrderByOrderAsc(surveyId);
		log.info("📋 조회된 질문 수: {}", questions.size());
		if (questions.isEmpty()) {
			log.warn("⚠️ surveyId={}에 대한 질문이 없습니다", surveyId);
			return Flux.empty();
		}

		FixedQuestion firstQuestion = questions.get(0);
		AnalysisCheckResult status = checkAnalysisStatus(firstQuestion);
		log.info("📊 분석 상태: {}", status);

		// FRESH 또는 IN_PROGRESS인 경우 캐시 반환
		if (status == AnalysisCheckResult.FRESH || status == AnalysisCheckResult.IN_PROGRESS) {
			List<QuestionResponseAnalysis> cachedResults = questionResponseAnalysisRepository
				.findAllBySurveyId(surveyId);
			log.info("💾 캐시된 분석 결과: {}개", cachedResults.size());
			return Flux.fromIterable(cachedResults)
				// 임시 분석중 데이터는 제외 (실제 결과만 반환)
				.filter(entity -> !entity.getResultJson().contains("\"status\":\"analyzing\""))
				.map(entity -> QuestionResponseAnalysisWrapper.builder()
					.fixedQuestionId(entity.getFixedQuestionId())
					.resultJson(entity.getResultJson())
					.build());
		}
		// STALE인 경우에만 재분석 (동시성 제어 포함)
		else {
			// 이미 분석 중이면 캐시 반환 (putIfAbsent는 기존 값이 없으면 null 반환)
			if (analysisInProgress.putIfAbsent(surveyId, Boolean.TRUE) != null) {
				log.info("🔒 이미 분석 진행 중: surveyId={}, 캐시 반환", surveyId);
				List<QuestionResponseAnalysis> cachedResults = questionResponseAnalysisRepository
					.findAllBySurveyId(surveyId);
				return Flux.fromIterable(cachedResults)
					.filter(entity -> entity.getResultJson() != null
						&& !entity.getResultJson().contains("\"status\":\"analyzing\""))
					.map(entity -> QuestionResponseAnalysisWrapper.builder()
						.fixedQuestionId(entity.getFixedQuestionId())
						.resultJson(entity.getResultJson())
						.build());
			}

			log.info("🔄 재분석 시작: {}개 질문", questions.size());
			return Flux.fromIterable(questions)
				.flatMap(question -> analyzeAndSave(survey.getUuid(), surveyId, question))
				.doFinally(signal -> {
					analysisInProgress.remove(surveyId);
					log.info("🔓 분석 완료, 잠금 해제: surveyId={}", surveyId);
				});
		}
	}

	/**
	 * 분석 상태 확인: FRESH(캐시 사용), IN_PROGRESS(진행중), STALE(재분석 필요)
	 */
	private AnalysisCheckResult checkAnalysisStatus(FixedQuestion question) {
		int currentCount = interviewLogRepository.countByFixedQuestionIdAndAnswerTextIsNotNull(question.getId());
		Optional<QuestionResponseAnalysis> cached = questionResponseAnalysisRepository.findByFixedQuestionId(
			question.getId());

		if (cached.isEmpty()) {
			return AnalysisCheckResult.STALE; // 분석된 적 없음
		}

		QuestionResponseAnalysis analysis = cached.get();

		// 진행 중이면 기존 결과 반환 (있으면)
		if (analysis.isInProgress()) {
			return AnalysisCheckResult.IN_PROGRESS;
		}

		// 완료되었고 최신 데이터면 캐시 사용
		if (analysis.isCompleted() && analysis.getProcessedAnswerCount() >= currentCount) {
			return AnalysisCheckResult.FRESH;
		}

		// 새로운 답변이 있으면 재분석 필요
		return AnalysisCheckResult.STALE;
	}

	private enum AnalysisCheckResult {
		FRESH, // 캐시 사용 가능
		IN_PROGRESS, // 분석 진행 중
		STALE // 재분석 필요
	}

	private Mono<QuestionResponseAnalysisWrapper> analyzeAndSave(UUID surveyUuid, Long surveyId,
		FixedQuestion question) {
		int currentCount = interviewLogRepository.countByFixedQuestionIdAndAnswerTextIsNotNull(question.getId());

		// 분석 시작 전에 IN_PROGRESS 상태로 변경 (별도 트랜잭션)
		markAsInProgressWithTransaction(question, currentCount);

		return aiClient.streamQuestionAnalysis(surveyUuid.toString(), question.getId())
			.filter(sse -> "done".equals(sse.event()))
			.next()
			.map(sse -> {
				String resultJson = sse.data();
				if (resultJson != null) {
					resultJson = convertAnswerIdsToTexts(resultJson);
					saveOrUpdateResultWithTransaction(question, resultJson, currentCount);
				}
				return QuestionResponseAnalysisWrapper.builder()
					.fixedQuestionId(question.getId())
					.resultJson(resultJson)
					.build();
			});
	}

	private String convertAnswerIdsToTexts(String resultJson) {
		try {
			JsonNode root = objectMapper.readTree(resultJson);
			if (!root.has("clusters")) {
				return resultJson;
			}

			ObjectNode rootNode = (ObjectNode)root;
			ArrayNode clusters = (ArrayNode)root.get("clusters");

			for (int i = 0; i < clusters.size(); i++) {
				ObjectNode cluster = (ObjectNode)clusters.get(i);
				if (cluster.has("representative_answer_ids")) {
					ArrayNode answerIds = (ArrayNode)cluster.get("representative_answer_ids");
					List<String> answerTexts = new ArrayList<>();

					for (JsonNode idNode : answerIds) {
						String answerId = idNode.asText();
						String answerText = fetchAnswerText(answerId);
						if (answerText != null) {
							answerTexts.add(answerText);
						}
					}

					// representative_answer_ids를 representative_answers로 교체
					cluster.remove("representative_answer_ids");
					ArrayNode answersArray = objectMapper.createArrayNode();
					answerTexts.forEach(answersArray::add);
					cluster.set("representative_answers", answersArray);
				}
			}

			// outliers 섹션도 처리
			if (root.has("outliers") && root.get("outliers").has("answer_ids")) {
				ObjectNode outliers = (ObjectNode)root.get("outliers");
				ArrayNode answerIds = (ArrayNode)outliers.get("answer_ids");
				List<String> answerTexts = new ArrayList<>();

				// outliers는 최대 5개만 변환 (너무 많으면 성능 이슈)
				int limit = Math.min(answerIds.size(), 5);
				for (int i = 0; i < limit; i++) {
					String answerId = answerIds.get(i).asText();
					String answerText = fetchAnswerText(answerId);
					if (answerText != null) {
						answerTexts.add(answerText);
					}
				}

				ArrayNode answersArray = objectMapper.createArrayNode();
				answerTexts.forEach(answersArray::add);
				outliers.set("sample_answers", answersArray);
			}

			return objectMapper.writeValueAsString(rootNode);
		} catch (Exception e) {
			log.warn("Failed to convert answer IDs to texts: {}", e.getMessage());
			return resultJson; // 변환 실패 시 원본 반환
		}
	}

	/**
	 * answer_id로부터 실제 답변 텍스트 조회
	 *
	 * @param answerId 형식: {session_uuid}_{fixed_question_id}_{hash}
	 *                 UUID는 하이픈 포함 36자 (예:
	 *                 ac1565f9-9b91-1346-819b-91c352bf002d_1_82c7e975)
	 * @return 포맷팅된 대화 텍스트 (Q&A 형식)
	 */
	private String fetchAnswerText(String answerId) {
		try {
			// answer_id 형식: {uuid}_{fixedQuestionId}_{hash}
			// UUID는 하이픈을 포함하므로 단순 split("_")으로는 파싱 불가
			// UUID 형식: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (36자)

			// 방법: 마지막 2개의 '_' 위치를 찾아서 파싱
			int lastUnderscore = answerId.lastIndexOf('_');
			if (lastUnderscore < 0) {
				log.debug("Invalid answer_id format (no underscore): {}", answerId);
				return null;
			}

			int secondLastUnderscore = answerId.lastIndexOf('_', lastUnderscore - 1);
			if (secondLastUnderscore < 0) {
				log.debug("Invalid answer_id format (need at least 2 underscores): {}", answerId);
				return null;
			}

			// UUID 추출 (처음부터 secondLastUnderscore까지)
			String sessionUuidStr = answerId.substring(0, secondLastUnderscore);
			// fixedQuestionId 추출 (secondLastUnderscore+1부터 lastUnderscore까지)
			String fixedQIdStr = answerId.substring(secondLastUnderscore + 1, lastUnderscore);
			// hash는 무시 (lastUnderscore+1부터 끝까지)

			Long fixedQuestionId = Long.parseLong(fixedQIdStr);
			UUID sessionUuid = UUID.fromString(sessionUuidStr);

			// UUID로 InterviewLog 조회
			List<InterviewLog> logs = interviewLogRepository
				.findBySessionUuidAndFixedQuestionIdOrderByTurnNumAsc(sessionUuid, fixedQuestionId);

			if (logs.isEmpty()) {
				log.debug("No logs found for session_uuid={}, fixed_question_id={}", sessionUuid, fixedQuestionId);
				return null;
			}

			// Q&A 형식으로 포맷팅
			return logs.stream()
				.filter(l -> l.getAnswerText() != null)
				.map(l -> String.format("Q: %s\nA: %s",
					l.getQuestionText(),
					l.getAnswerText()))
				.collect(Collectors.joining("\n\n"));
		} catch (IllegalArgumentException e) {
			log.debug("Failed to parse answer_id (invalid UUID or number): {}", answerId);
			return null;
		} catch (Exception e) {
			log.warn("Unexpected error fetching answer text for id={}: {}", answerId, e.getMessage());
			return null;
		}
	}

	/**
	 * TransactionTemplate을 사용하여 별도 트랜잭션에서 IN_PROGRESS 표시
	 */
	private void markAsInProgressWithTransaction(FixedQuestion question, int count) {
		transactionTemplate.executeWithoutResult(status -> {
			log.info("Marking analysis as IN_PROGRESS for surveyId={}, questionId={}", question.getSurveyId(),
				question.getId());

			questionResponseAnalysisRepository.findByFixedQuestionId(question.getId())
				.ifPresentOrElse(
					existing -> {
						existing.markInProgress();
						questionResponseAnalysisRepository.save(existing);
					},
					() -> questionResponseAnalysisRepository.save(new QuestionResponseAnalysis(
						question.getId(),
						question.getSurveyId(),
						"{\"status\":\"analyzing\"}", // 분석 진행 중 임시 JSON
						count)));
		});
	}

	/**
	 * TransactionTemplate을 사용하여 별도 트랜잭션에서 결과 저장
	 */
	private void saveOrUpdateResultWithTransaction(FixedQuestion question, String json, int count) {
		transactionTemplate.executeWithoutResult(status -> {
			log.info("Saving analysis result for surveyId={}, questionId={}, count={}", question.getSurveyId(),
				question.getId(), count);

			questionResponseAnalysisRepository.findByFixedQuestionId(question.getId())
				.ifPresentOrElse(
					existing -> {
						existing.updateResult(json, count);
						questionResponseAnalysisRepository.save(existing);
					},
					() -> questionResponseAnalysisRepository.save(new QuestionResponseAnalysis(
						question.getId(),
						question.getSurveyId(),
						json,
						count)));
		});
	}

	@Transactional
	protected void markAsInProgress(FixedQuestion question, int count) {
		log.info("Marking analysis as IN_PROGRESS for surveyId={}, questionId={}", question.getSurveyId(),
			question.getId());

		questionResponseAnalysisRepository.findByFixedQuestionId(question.getId())
			.ifPresentOrElse(
				existing -> {
					existing.markInProgress();
					questionResponseAnalysisRepository.save(existing);
				},
				() -> questionResponseAnalysisRepository.save(new QuestionResponseAnalysis(
					question.getId(),
					question.getSurveyId(),
					"{\"status\":\"analyzing\"}", // 분석 진행 중 임시 JSON
					count)));
	}

	@Transactional
	protected void saveOrUpdateResult(FixedQuestion question, String json, int count) {
		log.info("Saving analysis result for surveyId={}, questionId={}, count={}", question.getSurveyId(),
			question.getId(), count);

		questionResponseAnalysisRepository.findByFixedQuestionId(question.getId())
			.ifPresentOrElse(
				existing -> {
					existing.updateResult(json, count);
					questionResponseAnalysisRepository.save(existing);
				},
				() -> questionResponseAnalysisRepository.save(new QuestionResponseAnalysis(
					question.getId(),
					question.getSurveyId(),
					json,
					count)));
	}
}
