package com.playprobie.api.domain.analytics.application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.analytics.dao.QuestionResponseAnalysisRepository;
import com.playprobie.api.domain.analytics.domain.QuestionResponseAnalysis;
import com.playprobie.api.domain.analytics.dto.QuestionResponseAnalysisWrapper;
import com.playprobie.api.domain.interview.dao.InterviewLogRepository;
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

	/**
	 * 설문 전체 질문 분석 결과 조회 (캐시 or AI 분석)
	 */
	@Transactional
	public Flux<QuestionResponseAnalysisWrapper> getSurveyAnalysis(Long surveyId) {
		List<FixedQuestion> questions = fixedQuestionRepository.findBySurveyIdOrderByOrderAsc(surveyId);
		if (questions.isEmpty()) {
			return Flux.empty();
		}

		FixedQuestion firstQuestion = questions.get(0);
		AnalysisCheckResult status = checkAnalysisStatus(firstQuestion);

		// FRESH 또는 IN_PROGRESS인 경우 캐시 반환
		if (status == AnalysisCheckResult.FRESH || status == AnalysisCheckResult.IN_PROGRESS) {
			return Flux.fromIterable(questionResponseAnalysisRepository.findAllBySurveyId(surveyId))
					// 임시 분석중 데이터는 제외 (실제 결과만 반환)
					.filter(entity -> !entity.getResultJson().contains("\"status\":\"analyzing\""))
					.map(entity -> QuestionResponseAnalysisWrapper.builder()
							.fixedQuestionId(entity.getFixedQuestionId())
							.resultJson(entity.getResultJson())
							.build());
		} 
		// STALE인 경우에만 재분석
		else {
			return Flux.fromIterable(questions)
					.flatMap(question -> analyzeAndSave(surveyId, question));
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
		FRESH,       // 캐시 사용 가능
		IN_PROGRESS, // 분석 진행 중
		STALE        // 재분석 필요
	}

	private Mono<QuestionResponseAnalysisWrapper> analyzeAndSave(Long surveyId, FixedQuestion question) {
		int currentCount = interviewLogRepository.countByFixedQuestionIdAndAnswerTextIsNotNull(question.getId());

		// 분석 시작 전에 IN_PROGRESS 상태로 변경
		markAsInProgress(question, currentCount);

		return aiClient.streamQuestionAnalysis(surveyId, question.getId())
				.filter(sse -> "done".equals(sse.event()))
				.next()
				.map(sse -> {
					String resultJson = sse.data();
					if (resultJson != null) {
						saveOrUpdateResult(question, resultJson, currentCount);
					}
					return QuestionResponseAnalysisWrapper.builder()
							.fixedQuestionId(question.getId())
							.resultJson(resultJson)
							.build();
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
