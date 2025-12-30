package com.playprobie.api.domain.survey.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 질문 피드백 요청 DTO
 * POST /surveys/question-feedback
 */
public record QuestionFeedbackRequest(
        @NotBlank(message = "게임 이름은 필수입니다") @JsonProperty("game_name") String gameName,

        @NotBlank(message = "게임 설명은 필수입니다") @JsonProperty("game_context") String gameContext,

        @NotEmpty(message = "게임 장르는 필수입니다") @JsonProperty("game_genre") List<String> gameGenre,

        @NotBlank(message = "설문 이름은 필수입니다") @JsonProperty("survey_name") String surveyName,

        @NotBlank(message = "테스트 목적은 필수입니다") @JsonProperty("test_purpose") String testPurpose,

        @NotEmpty(message = "질문 목록은 필수입니다") @JsonProperty("questions") List<String> questions) {
}
