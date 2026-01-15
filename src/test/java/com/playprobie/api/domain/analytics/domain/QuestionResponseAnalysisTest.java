package com.playprobie.api.domain.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestionResponseAnalysisTest {

	@Test
	@DisplayName("QuestionResponseAnalysis 생성 및 조회")
	void createEntity() {
		// Given
		Long surveyId = 1L;
		Long questionId = 101L;
		String json = "{\"test\":\"data\"}";
		Integer count = 50;

		// When
		QuestionResponseAnalysis result = new QuestionResponseAnalysis(questionId, surveyId, json, count);

		// Then
		assertThat(result.getSurveyId()).isEqualTo(surveyId);
		assertThat(result.getFixedQuestionId()).isEqualTo(questionId);
		assertThat(result.getResultJson()).isEqualTo(json);
		assertThat(result.getProcessedAnswerCount()).isEqualTo(count);
	}

	@Test
	@DisplayName("QuestionResponseAnalysis 업데이트")
	void updateResult() {
		// Given
		QuestionResponseAnalysis result = new QuestionResponseAnalysis(101L, 1L, "old", 10);

		// When
		result.updateResult("new", 20);

		// Then
		assertThat(result.getResultJson()).isEqualTo("new");
		assertThat(result.getProcessedAnswerCount()).isEqualTo(20);
	}

}
