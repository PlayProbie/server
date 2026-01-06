package com.playprobie.api.domain.survey.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "질문 피드백 요청 DTO")
public record QuestionFeedbackRequest(

		@Schema(description = "게임 이름", example = "My RPG Game", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "게임 이름은 필수입니다") @JsonProperty("game_name") String gameName,

		@Schema(description = "게임 설명", example = "중세 판타지 배경의 RPG 게임입니다.", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "게임 설명은 필수입니다") @JsonProperty("game_context") String gameContext,

		@Schema(description = "게임 장르 목록", example = "[\"RPG\", \"ACTION\"]", requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty(message = "게임 장르는 필수입니다") @JsonProperty("game_genre") List<String> gameGenre,

		@Schema(description = "설문 이름", example = "출시 전 테스트 설문", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "설문 이름은 필수입니다") @JsonProperty("survey_name") String surveyName,

		@Schema(description = "테스트 목적", example = "전투 밸런스 피드백 수집", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "테스트 목적은 필수입니다") @JsonProperty("test_purpose") String testPurpose,

		@Schema(description = "피드백을 받을 질문 목록", example = "[\"게임 난이도는 적절한가요?\", \"전투 시스템은 재미있었나요?\"]", requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty(message = "질문 목록은 필수입니다") @JsonProperty("questions") List<String> questions) {
}
