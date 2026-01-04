package com.playprobie.api.domain.survey.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

public record UpdateQuestionRequest(
	@NotBlank(message = "질문 내용은 필수입니다") @JsonProperty("q_content") String qContent) {
}
