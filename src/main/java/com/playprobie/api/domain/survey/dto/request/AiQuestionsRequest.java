package com.playprobie.api.domain.survey.dto.request;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "AI 질문 생성 요청 DTO")
public record AiQuestionsRequest(

		@Schema(description = "게임 이름", example = "My RPG Game", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "게임 이름은 필수입니다") @JsonProperty("game_name") String gameName,

		@Schema(description = "게임 설명", example = "중세 판타지 배경의 RPG 게임입니다.", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "게임 설명은 필수입니다") @JsonProperty("game_context") String gameContext,

		@Schema(description = "게임 장르 목록", example = "[\"RPG\", \"ACTION\"]", requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty(message = "게임 장르는 필수입니다") @JsonProperty("game_genre") List<String> gameGenre,

		@Schema(description = "설문 이름", example = "출시 전 테스트 설문", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "설문 이름은 필수입니다") @JsonProperty("survey_name") String surveyName,

		@Schema(description = "테마 우선순위 (1~3개)", example = "[\"gameplay\", \"ui_ux\", \"balance\"]", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "테마 우선순위는 필수입니다") @Size(min = 1, max = 3, message = "테마는 1~3개 선택해야 합니다") @JsonProperty("theme_priorities") List<String> themePriorities,

		@Schema(description = "테마별 세부사항", example = "{\"gameplay\": [\"core_loop\", \"fun\"]}", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonProperty("theme_details") Map<String, List<String>> themeDetails,

		@Schema(description = "생성할 질문 개수 (최소 1개)", example = "5", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "질문 개수는 필수입니다") @Min(value = 1, message = "질문 개수는 1 이상이어야 합니다") @JsonProperty("count") Integer count) {
}
