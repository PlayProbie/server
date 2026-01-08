package com.playprobie.api.infra.ai.dto.request;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "AI 질문 생성 요청 DTO")
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GenerateQuestionRequest {

	@Schema(description = "게임 이름", example = "My RPG Game")
	private String gameName;

	@Schema(description = "게임 장르", example = "RPG")
	private String gameGenre;

	@Schema(description = "게임 설명", example = "중세 판타지 배경의 오픈월드 게임")
	private String gameContext;

	@Schema(description = "테마 우선순위", example = "[\"gameplay\", \"ui_ux\"]")
	private List<String> themePriorities;

	@Schema(description = "테마별 세부사항", example = "{\"gameplay\": [\"core_loop\"]}")
	private Map<String, List<String>> themeDetails;
}
