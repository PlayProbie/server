package com.playprobie.api.domain.survey.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * AI 질문 생성 요청 DTO
 * POST /surveys/ai-questions
 */
public record AiQuestionsRequest(
	@NotBlank(message = "게임 이름은 필수입니다") @JsonProperty("game_name") String gameName,
	@NotBlank(message = "게임 설명은 필수입니다") @JsonProperty("game_context") String gameContext,
	@NotEmpty(message = "게임 장르는 필수입니다") @JsonProperty("game_genre") List<String> gameGenre,
	@NotBlank(message = "설문 이름은 필수입니다") @JsonProperty("survey_name") String surveyName,
	@NotBlank(message = "테스트 목적은 필수입니다") @JsonProperty("test_purpose") String testPurpose,
	@NotNull(message = "질문 개수는 필수입니다") @Min(value = 1, message = "질문 개수는 1 이상이어야 합니다") @JsonProperty("count") Integer count) {
}
