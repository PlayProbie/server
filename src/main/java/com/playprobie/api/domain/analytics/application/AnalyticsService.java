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
		boolean isFresh = checkIsFresh(firstQuestion);

		if (isFresh) {
			return Flux.fromIterable(questionResponseAnalysisRepository.findAllBySurveyId(surveyId))
					.map(entity -> QuestionResponseAnalysisWrapper.builder()
							.fixedQuestionId(entity.getFixedQuestionId())
							.resultJson(entity.getResultJson())
							.build());
		} else {
			return Flux.fromIterable(questions)
					.flatMap(question -> analyzeAndSave(surveyId, question));
		}
	}

	private boolean checkIsFresh(FixedQuestion question) {
		int currentCount = interviewLogRepository.countByFixedQuestionIdAndAnswerTextIsNotNull(question.getId());
		Optional<QuestionResponseAnalysis> cached = questionResponseAnalysisRepository.findByFixedQuestionId(
				question.getId());

		if (cached.isEmpty()) {
			return false;
		}

		return cached.get().getProcessedAnswerCount() >= currentCount;
	}

	private Mono<QuestionResponseAnalysisWrapper> analyzeAndSave(Long surveyId, FixedQuestion question) {
		int currentCount = interviewLogRepository.countByFixedQuestionIdAndAnswerTextIsNotNull(question.getId());

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
