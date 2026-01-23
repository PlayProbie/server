package com.playprobie.api.domain.survey.dto.request;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Schema(description = "AI 질문 생성 요청 DTO")
public record AiQuestionsRequest(

	@Schema(description = "게임 이름", example = "My RPG Game", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "게임 이름은 필수입니다") @JsonProperty("game_name")
	String gameName,

	@Schema(description = "게임 장르 목록", example = "[\"RPG\", \"ACTION\"]", requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty(message = "게임 장르는 필수입니다") @JsonProperty("game_genre")
	List<String> gameGenre,

	@Schema(description = "게임 설명", example = "중세 판타지 배경의 RPG 게임입니다.", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "게임 설명은 필수입니다") @JsonProperty("game_context")
	String gameContext,

	@Schema(description = "테마 우선순위 (1~3개)", example = "[\"gameplay\", \"ui_ux\"]", requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty(message = "테마 우선순위는 필수입니다") @Size(min = 1, max = 3, message = "테마 우선순위는 1~3개여야 합니다") @JsonProperty("theme_priorities")
	List<String> themePriorities,

	@Schema(description = "테마별 세부 항목 (선택)", example = "{\"gameplay\": [\"core_loop\", \"fun\"], \"ui_ux\": [\"onboarding\"]}", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonProperty("theme_details")
	Map<String, List<String>> themeDetails,

	@Schema(description = "요청할 질문 개수 (기본값: 5)", example = "5", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonProperty("count")
	Integer count,

	@Schema(description = "테스트 단계 (prototype/playtest/launch)", example = "prototype", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonProperty("test_stage")
	String testStage,

	@Schema(description = "게임 UUID (DB 조회용)", example = "123e4567-e89b-12d3-a456-426614174000", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonProperty("game_uuid")
	java.util.UUID gameUuid,

	@Schema(description = "게임 핵심 요소 (template 치환용)", example = "{\"core_mechanic\": \"Deck Building\", \"player_goal\": \"Climb the Spire\"}", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonProperty("extracted_elements")
	Map<String, String> extractedElements,

	@Schema(description = "질문 셔플 여부 (true: 다양한 추천, false/null: 최적 매칭)", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonProperty("shuffle")
	Boolean shuffle) {
}
