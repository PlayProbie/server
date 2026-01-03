package com.playprobie.api.domain.survey.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 질문 피드백 응답 항목 DTO
 * POST /surveys/question-feedback 응답의 각 항목
 */
public record QuestionFeedbackResponse(
	@JsonProperty("question") String question,
	@JsonProperty("ai_feedback") String aiFeedback,
	@JsonProperty("suggestions") List<String> suggestions) {
}
