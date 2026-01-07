package com.playprobie.api.infra.ai.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "AI 피드백 생성 요청 DTO")
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GenerateFeedbackRequest {

	@Schema(description = "게임 이름", example = "My RPG Game")
	private String gameName;

	@Schema(description = "게임 장르", example = "RPG")
	private String gameGenre;

	@Schema(description = "게임 설명", example = "중세 판타지 배경의 오픈월드 게임")
	private String gameContext;

	@Schema(description = "테스트 목적", example = "게임 밸런스 확인")
	private String testPurpose;

	@Schema(description = "원본 질문", example = "게임 난이도는 어땠나요?")
	private String originalQuestion;
}
