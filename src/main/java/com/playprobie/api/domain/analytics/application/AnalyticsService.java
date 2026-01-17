package com.playprobie.api.domain.analytics.application;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playprobie.api.domain.analytics.dao.QuestionResponseAnalysisRepository;
import com.playprobie.api.domain.analytics.domain.AnalysisStatus;
import com.playprobie.api.domain.analytics.domain.QuestionResponseAnalysis;
import com.playprobie.api.domain.analytics.dto.AnalyticsResponse;
import com.playprobie.api.domain.analytics.dto.QuestionResponseAnalysisWrapper;
import com.playprobie.api.domain.analytics.dto.analysis.AnswerProfile;
import com.playprobie.api.domain.analytics.dto.analysis.ClusterInfo;
import com.playprobie.api.domain.analytics.dto.analysis.QuestionAnalysisOutput;
import com.playprobie.api.domain.analytics.event.AnalyticsUpdatedEvent;
import com.playprobie.api.domain.interview.dao.InterviewLogRepository;
import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.SessionStatus;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.interview.domain.TesterProfile;
import com.playprobie.api.domain.survey.dao.FixedQuestionRepository;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.FixedQuestion;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;
import com.playprobie.api.infra.ai.AiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.playprobie.api.domain.analytics.dao.FilteredQuestionAnalysisRepository;
import com.playprobie.api.domain.analytics.domain.FilteredQuestionAnalysis;
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
	private final SurveyRepository surveyRepository;
	private final SurveySessionRepository surveySessionRepository;
	private final FilteredQuestionAnalysisRepository filteredQuestionAnalysisRepository;
	private final ObjectMapper objectMapper;

	private final ApplicationEventPublisher eventPublisher;

	private final TransactionTemplate transactionTemplate;

	/**
	 * 설문 전체 질문 분석 결과 조회 (캐시 or AI 분석)
	 * - MockDataLoader 등에서 AI 분석 트리거용으로 사용
	 */
	public Flux<QuestionResponseAnalysisWrapper> triggerAnalytics(UUID surveyUuid) {
		log.info("🔍 분석 요청: surveyUuid={}", surveyUuid);

		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
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
				.map(entity -> QuestionResponseAnalysisWrapper.builder()
					.fixedQuestionId(entity.getFixedQuestionId())
					.resultJson(entity.getResultJson())
					.build());
		}
		// STALE인 경우에만 재분석
		else {
			log.info("📢 재분석 시작: {}개 질문", questions.size());
			// 분석 결과 수집용 리스트
			List<QuestionResponseAnalysisWrapper> analysisResults = java.util.Collections
				.synchronizedList(new java.util.ArrayList<>());

			return Flux.fromIterable(questions)
				.flatMap(question -> analyzeAndSave(surveyUuid, surveyId, question))
				.doOnNext(analysisResults::add) // 결과 수집
				.doOnComplete(() -> {
					log.info("📢 모든 질문 분석 완료. 설문 종합 평가 생성 시작: surveyUuid={}", surveyUuid);

					List<String> metaSummaries = extractMetaSummaries(analysisResults);
					if (!metaSummaries.isEmpty()) {
						aiClient.generateSurveySummary(metaSummaries)
							.doOnSuccess(summary -> saveSurveySummary(surveyUuid, summary))
							.subscribe();
					} else {
						log.warn("⚠️ 메타 요약이 없어 설문 종합 평가를 건너뜁니다.");
					}

					eventPublisher.publishEvent(new AnalyticsUpdatedEvent(surveyUuid));
				});
		}
	}

	/**
	 * 단일 질문 분석 요청 (Event Listener 등에서 호출)
	 */
	@Transactional
	public void analyzeSingleQuestion(UUID surveyUuid, Long fixedQuestionId) {
		log.info("🔍 단일 질문 분석 요청: surveyUuid={}, fixedQuestionId={}", surveyUuid, fixedQuestionId);

		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

		FixedQuestion question = fixedQuestionRepository.findById(fixedQuestionId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

		analyzeAndSave(surveyUuid, survey.getId(), question)
			.doOnSuccess(result -> {
				long totalCount = fixedQuestionRepository.countBySurveyId(survey.getId());
				long completedCount = questionResponseAnalysisRepository.countBySurveyIdAndStatus(survey.getId(),
					QuestionResponseAnalysis.AnalysisStatus.COMPLETED);

				log.info("📊 분석 진행 상황: {}/{} (surveyUuid={})", completedCount, totalCount, surveyUuid);

				if (completedCount >= totalCount) {
					log.info("📢 모든 질문 분석 완료, SSE 이벤트 발행: surveyUuid={}", surveyUuid);
					eventPublisher.publishEvent(new AnalyticsUpdatedEvent(surveyUuid));
				}
			})
			.subscribe();
	}

	/**
	 * 설문 분석 결과 동기 조회 (REST API용)
	 * - DB에 캐시된 분석 결과만 반환
	 * - AI 분석은 MockDataLoader에서 사전 수행됨
	 */
	public AnalyticsResponse getSurveyAnalysis(UUID surveyUuid, Map<String, String> filters) {
		// 필터가 없거나 비어있으면 기존 로직 수행
		if (filters == null || filters.isEmpty() || filters.values().stream().allMatch(v -> v == null || v.isBlank())) {
			return getSurveyAnalysis(surveyUuid);
		}

		log.info("🔍 필터링된 분석 결과 조회: surveyUuid={}, filters={}", surveyUuid, filters);
		String filterSignature = generateFilterSignature(filters);

		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
		Long surveyId = survey.getId();

		List<FixedQuestion> questions = fixedQuestionRepository
			.findBySurveyIdOrderByOrderAsc(surveyId);

		if (questions.isEmpty()) {
			return buildAnalyticsResponse(List.of(), 0, 0, "", false);
		}

		// 필터링된 결과 조회 (Cache Look-aside)
		List<QuestionResponseAnalysisWrapper> analyses = new java.util.ArrayList<>();
		List<Long> cacheMissQuestionIds = new java.util.ArrayList<>();

		for (FixedQuestion question : questions) {
			Optional<FilteredQuestionAnalysis> cached = filteredQuestionAnalysisRepository
				.findByFixedQuestionIdAndFilterSignature(question.getId(), filterSignature);

			if (cached.isPresent()) {
				// Cache Hit
				analyses.add(QuestionResponseAnalysisWrapper.builder()
					.fixedQuestionId(cached.get().getFixedQuestionId())
					.resultJson(cached.get().getResultJson())
					.build());
			} else {
				// Cache Miss -> 나중에 일괄 처리
				cacheMissQuestionIds.add(question.getId());
			}
		}

		// Cache Miss 질문들에 대해 비동기 분석 트리거 (모든 완료 후 SSE 발행)
		boolean hasInProgress = !cacheMissQuestionIds.isEmpty();
		if (hasInProgress) {
			AtomicInteger remainingCount = new AtomicInteger(cacheMissQuestionIds.size());
			for (Long questionId : cacheMissQuestionIds) {
				triggerFilteredAnalysis(surveyUuid.toString(), questionId, filters, filterSignature, remainingCount,
					surveyUuid);
			}
		}

		int totalParticipants = (int)surveySessionRepository.countBySurveyIdAndStatus(surveyId,
			com.playprobie.api.domain.interview.domain.SessionStatus.COMPLETED);

		// 필터링된 결과의 상태 판단:
		// hasInProgress가 true면 IN_PROGRESS 반환
		return buildAnalyticsResponse(analyses, questions.size(), totalParticipants, "", hasInProgress);
	}

	public AnalyticsResponse getSurveyAnalysis(UUID surveyUuid) {
		// 기존 로직 유지 (Overloading)
		log.info("🔍 분석 결과 조회 (Sync): surveyUuid={}", surveyUuid);

		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
		Long surveyId = survey.getId();

		List<FixedQuestion> questions = fixedQuestionRepository
			.findBySurveyIdOrderByOrderAsc(surveyId);

		if (questions.isEmpty()) {
			log.warn("⚠️ surveyId={}에 대한 질문이 없습니다", surveyId);
			return buildAnalyticsResponse(List.of(), 0, 0, "", false);
		}

		// DB에서 완료된 분석 결과만 조회
		List<QuestionResponseAnalysis> cachedResults = questionResponseAnalysisRepository
			.findAllBySurveyId(surveyId)
			.stream()
			.filter(entity -> entity.getResultJson() != null)
			.toList();

		// IN_PROGRESS 상태 체크
		boolean hasInProgress = questionResponseAnalysisRepository
			.findAllBySurveyId(surveyId)
			.stream()
			.anyMatch(QuestionResponseAnalysis::isInProgress);

		List<QuestionResponseAnalysisWrapper> analyses = cachedResults.stream()
			.map(entity -> QuestionResponseAnalysisWrapper.builder()
				.fixedQuestionId(entity.getFixedQuestionId())
				.resultJson(entity.getResultJson())
				.build())
			.toList();

		log.info("📊 분석 결과 조회 완료: {}개 질문 중 {}개 완료",
			questions.size(), analyses.size());

		int totalParticipants = (int)surveySessionRepository.countBySurveyIdAndStatus(surveyId,
			SessionStatus.COMPLETED);

		// 설문 종합 평가 포함
		String surveySummary = survey.getSurveySummary();

		return buildAnalyticsResponse(analyses, questions.size(), totalParticipants, surveySummary, hasInProgress);
	}

	/**
	 * 분석 결과와 전체 질문 수를 기반으로 AnalyticsResponse 생성
	 * 상태 결정 로직:
	 * - 분석 결과가 없으면 NO_DATA
	 * - 유효한 분석(clusters가 있는)이 하나도 없으면 INSUFFICIENT_DATA
	 * - 분석 진행 중인 질문이 있으면 IN_PROGRESS
	 * - 유효한 분석이 1개 이상 있으면 COMPLETED
	 *
	 * 참고: validity/quality 필터링으로 일부 질문의 답변이 전부 제외될 수 있으므로,
	 * 유효한 분석 개수가 전체 질문 수보다 적어도 COMPLETED로 처리함
	 */
	private AnalyticsResponse buildAnalyticsResponse(
		List<QuestionResponseAnalysisWrapper> analyses,
		int totalQuestions,
		int totalParticipants,
		String surveySummary,
		boolean hasInProgress) {

		// 유효한 분석 결과만 필터링 (clusters가 있고 비어있지 않은 것)
		long validAnalysesCount = analyses.stream()
			.filter(this::isValidAnalysisResult)
			.count();

		AnalysisStatus status;

		if (analyses.isEmpty()) {
			status = hasInProgress ? AnalysisStatus.IN_PROGRESS : AnalysisStatus.NO_DATA;
		} else if (validAnalysesCount == 0) {
			// 분석 결과는 있지만 모두 유효하지 않음 (데이터 부족)
			status = hasInProgress ? AnalysisStatus.IN_PROGRESS : AnalysisStatus.INSUFFICIENT_DATA;
		} else if (hasInProgress) {
			status = AnalysisStatus.IN_PROGRESS;
		} else {
			// 유효한 분석이 1개 이상 있으면 COMPLETED
			// (필터링/validity로 일부 질문 데이터가 없을 수 있으므로 totalQuestions와 비교하지 않음)
			status = AnalysisStatus.COMPLETED;
		}

		return new AnalyticsResponse(analyses, status.name(), totalQuestions, (int)validAnalysesCount,
			totalParticipants,
			surveySummary);
	}

	/**
	 * 분석 결과 JSON이 유효한지 검사
	 * - clusters 필드가 존재하고 비어있지 않아야 유효
	 * - {"status":"analyzing"} 같은 진행 중 상태는 유효하지 않음
	 */
	private boolean isValidAnalysisResult(QuestionResponseAnalysisWrapper wrapper) {
		String json = wrapper.resultJson();
		if (json == null || json.isBlank()) {
			return false;
		}

		try {
			com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);

			// clusters 필드가 있고 배열이며 비어있지 않은지 확인
			if (node.has("clusters")) {
				com.fasterxml.jackson.databind.JsonNode clusters = node.get("clusters");
				return clusters.isArray() && clusters.size() > 0;
			}

			return false;
		} catch (JsonProcessingException e) {
			log.warn("Failed to parse analysis result JSON for validation", e);
			return false;
		}
	}

	// ... (checkAnalysisStatus, AnalysisCheckResult methods remain same)

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

		return aiClient.streamQuestionAnalysis(surveyUuid.toString(), question.getId(), null)
			.filter(sse -> "done".equals(sse.event()))
			.next()
			.map(sse -> {
				String resultJson = sse.data();
				if (resultJson != null) {
					try {
						resultJson = enrichAnalysisResult(resultJson);
					} catch (Exception e) {
						log.error("Failed to enrich analysis result", e);
						// 실패해도 원본 저장을 위해 진행
					}
					saveOrUpdateResultWithTransaction(surveyUuid, question, resultJson, currentCount);
				}
				return QuestionResponseAnalysisWrapper.builder()
					.fixedQuestionId(question.getId())
					.resultJson(resultJson)
					.build();
			});
	}

	/**
	 * 각 질문 분석 결과(JSON)에서 meta_summary 추출
	 */
	private List<String> extractMetaSummaries(List<QuestionResponseAnalysisWrapper> results) {
		return results.stream()
			.map(QuestionResponseAnalysisWrapper::resultJson)
			.map(json -> {
				try {
					com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
					if (node.has("meta_summary")) {
						return node.get("meta_summary").asText();
					}
				} catch (JsonProcessingException e) {
					log.error("Failed to parse meta_summary from json", e);
				}
				return null;
			})
			.filter(java.util.Objects::nonNull)
			.toList();
	}

	/**
	 * 설문 종합 평가 DB 저장 (별도 트랜잭션)
	 */
	private void saveSurveySummary(UUID surveyUuid, String summary) {
		if (summary == null || summary.isBlank()) {
			return;
		}
		transactionTemplate.executeWithoutResult(status -> {
			Survey survey = surveyRepository.findByUuid(surveyUuid)
				.orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
			survey.updateSurveySummary(summary);
			surveyRepository.save(survey);
			log.info("💾 설문 종합 평가 저장 완료: surveyUuid={}", surveyUuid);
		});
	}

	/**
	 * AI 분석 결과(JSON)에 유저 세그먼트 정보(AnswerProfile)를 추가합니다.
	 */
	private String enrichAnalysisResult(String json) {
		try {
			QuestionAnalysisOutput output = objectMapper.readValue(json, QuestionAnalysisOutput.class);
			Set<UUID> sessionUuids = new HashSet<>();

			// 1. 모든 클러스터에서 answer_ids 추출하여 Session UUID 수집
			if (output.getClusters() != null) {
				for (ClusterInfo cluster : output.getClusters()) {
					if (cluster.getAnswerIds() != null) {
						for (String answerId : cluster.getAnswerIds()) {
							extractSessionUuid(answerId).ifPresent(sessionUuids::add);
						}
					}
				}
			}

			// 2. Outliers에서도 추출
			if (output.getOutliers() != null && output.getOutliers().getAnswerIds() != null) {
				for (String answerId : output.getOutliers().getAnswerIds()) {
					extractSessionUuid(answerId).ifPresent(sessionUuids::add);
				}
			}

			if (sessionUuids.isEmpty()) {
				return json;
			}

			// 3. DB에서 Session 및 TesterProfile 조회
			Map<UUID, TesterProfile> profileMap = surveySessionRepository.findAllByUuidIn(sessionUuids).stream()
				.collect(Collectors.toMap(
					SurveySession::getUuid,
					session -> session.getTesterProfile() != null ? session.getTesterProfile()
						: TesterProfile.createAnonymous("Unknown", "Unknown", "Unknown")));

			// 4. AnswerProfile 매핑 생성 및 통계 집계
			Map<String, AnswerProfile> answerProfiles = new HashMap<>();
			Map<String, Integer> ageGroupStats = new HashMap<>();
			Map<String, Integer> genderStats = new HashMap<>();
			Map<String, Integer> genreStats = new HashMap<>();

			// Helper to process answer IDs
			java.util.function.Consumer<List<String>> processAnswerIds = (ids) -> {
				if (ids == null)
					return;
				for (String answerId : ids) {
					extractSessionUuid(answerId).ifPresent(uuid -> {
						TesterProfile tester = profileMap.get(uuid);
						if (tester != null) {
							answerProfiles.put(answerId, AnswerProfile.builder()
								.ageGroup(tester.getAgeGroup())
								.gender(tester.getGender())
								.preferGenre(tester.getPreferGenre())
								.build());

							// Age Group
							String age = tester.getAgeGroup() != null ? tester.getAgeGroup() : "Unknown";
							ageGroupStats.merge(age, 1, Integer::sum);

							// Gender
							String gender = tester.getGender() != null ? tester.getGender() : "Unknown";
							genderStats.merge(gender, 1, Integer::sum);

							// Genre (Comma separated)
							if (tester.getPreferGenre() != null && !tester.getPreferGenre().isEmpty()) {
								for (String genre : tester.getPreferGenre().split(",")) {
									genreStats.merge(genre.trim(), 1, Integer::sum);
								}
							}
						}
					});
				}
			};

			if (output.getClusters() != null) {
				output.getClusters().forEach(c -> processAnswerIds.accept(c.getAnswerIds()));
			}
			if (output.getOutliers() != null) {
				processAnswerIds.accept(output.getOutliers().getAnswerIds());
			}

			output.setAnswerProfiles(answerProfiles);
			output.setParticipantStats(QuestionAnalysisOutput.ParticipantStats.builder()
				.ageGroups(ageGroupStats)
				.genders(genderStats)
				.genres(genreStats)
				.build());

			return objectMapper.writeValueAsString(output);
		} catch (JsonProcessingException e) {
			log.error("Error parsing/writing analysis JSON", e);
			throw new RuntimeException(e);
		}
	}

	private Optional<UUID> extractSessionUuid(String answerId) {
		try {
			// answerId format: {session_uuid}_{fixed_question_id}_{hash}
			String[] parts = answerId.split("_");
			if (parts.length >= 1) {
				return Optional.of(UUID.fromString(parts[0]));
			}
		} catch (IllegalArgumentException e) {
			log.warn("Invalid UUID in answerId: {}", answerId);
		}
		return Optional.empty();
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
	private void saveOrUpdateResultWithTransaction(UUID surveyUuid, FixedQuestion question, String json, int count) {
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
			// 이벤트 발행은 triggerAnalytics()의 doOnComplete()에서 설문 단위로 한 번만 수행
		});
	}

	// ========================================================================
	// Filtered Analysis Helpers
	// ========================================================================

	private String generateFilterSignature(Map<String, String> filters) {
		return filters.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(e -> e.getKey() + "=" + e.getValue())
			.collect(Collectors.joining("|"));
	}

	/**
	 * 비동기로 Filtered Analysis 트리거 (Fire-and-forget)
	 *
	 * @param remainingCount 남은 분석 개수 카운터 (모든 완료 후 SSE 발행용)
	 * @param surveyUuid     SSE 이벤트 발행용 UUID
	 */
	private void triggerFilteredAnalysis(String surveyUuidStr, Long fixedQuestionId, Map<String, String> filters,
		String filterSignature, AtomicInteger remainingCount, UUID surveyUuid) {
		log.info("🚀 Triggering Async Filtered Analysis: qId={}, filters={}, remaining={}",
			fixedQuestionId, filters, remainingCount.get());

		aiClient.streamQuestionAnalysis(surveyUuidStr, fixedQuestionId, filters)
			.filter(sse -> "done".equals(sse.event()))
			.next()
			.subscribe(sse -> {
				String resultJson = sse.data();
				if (resultJson != null) {
					try {
						resultJson = enrichAnalysisResult(resultJson);
					} catch (Exception e) {
						log.warn("Failed to enrich filtered result", e);
					}
					saveFilteredResult(fixedQuestionId, filterSignature, resultJson, remainingCount, surveyUuid);
				} else {
					// 결과가 null이어도 카운터 감소 (실패 처리)
					decrementAndNotifyIfComplete(remainingCount, surveyUuid);
				}
			}, error -> {
				log.error("❌ Filtered Analysis Failed: qId={}", fixedQuestionId, error);
				// 에러 발생 시에도 카운터 감소 (다른 질문들의 완료를 막지 않음)
				decrementAndNotifyIfComplete(remainingCount, surveyUuid);
			});
	}

	private void saveFilteredResult(Long fixedQuestionId, String filterSignature, String resultJson,
		AtomicInteger remainingCount, UUID surveyUuid) {
		transactionTemplate.executeWithoutResult(status -> {
			filteredQuestionAnalysisRepository.findByFixedQuestionIdAndFilterSignature(fixedQuestionId, filterSignature)
				.ifPresentOrElse(
					existing -> {
						existing.updateResultJson(resultJson);
						filteredQuestionAnalysisRepository.save(existing);
						log.debug("✅ Filtered Analysis Updated: qId={}, sig={}", fixedQuestionId,
							filterSignature);
					},
					() -> {
						filteredQuestionAnalysisRepository.save(
							FilteredQuestionAnalysis.builder()
								.fixedQuestionId(fixedQuestionId)
								.filterSignature(filterSignature)
								.resultJson(resultJson)
								.build());
						log.debug("✅ Filtered Analysis Created: qId={}, sig={}", fixedQuestionId,
							filterSignature);
					});
		});

		// 카운터 감소 후 모든 분석 완료 시 SSE 알림
		decrementAndNotifyIfComplete(remainingCount, surveyUuid);
	}

	/**
	 * 남은 분석 카운터를 감소시키고, 모든 분석 완료 시 SSE 이벤트 발행
	 */
	private void decrementAndNotifyIfComplete(AtomicInteger remainingCount, UUID surveyUuid) {
		int remaining = remainingCount.decrementAndGet();
		log.debug("📊 Filtered Analysis Progress: remaining={}, surveyUuid={}", remaining, surveyUuid);

		if (remaining == 0) {
			log.info("📢 All Filtered Analyses Complete -> Notify SSE: surveyUuid={}", surveyUuid);
			eventPublisher.publishEvent(new AnalyticsUpdatedEvent(surveyUuid));
		}
	}
}
