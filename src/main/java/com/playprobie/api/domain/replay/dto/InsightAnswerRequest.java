package com.playprobie.api.domain.replay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * 인사이트 질문 답변 Request
 * POST /sessions/{sessionId}/replay/insights/{tagId}/answer
 */
public record InsightAnswerRequest(
	@NotBlank @JsonProperty("answer_text")
	String answerText) {
}
