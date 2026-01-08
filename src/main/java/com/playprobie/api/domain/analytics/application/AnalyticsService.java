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
						saveOrUpdateResultWithTransaction(question, resultJson, currentCount);
					}
					return QuestionResponseAnalysisWrapper.builder()
							.fixedQuestionId(question.getId())
							.resultJson(resultJson)
							.build();
				});
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
