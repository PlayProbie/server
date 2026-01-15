package com.playprobie.api.infra.ai.dto.response;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "AI 피드백 생성 응답 DTO")
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GenerateFeedbackResponse {

	@Schema(description = "생성된 피드백", example = "질문이 너무 추상적입니다.")
	private String feedback;

	@Schema(description = "대체 질문 후보 목록", example = "[\"구체적으로 어떤 점이 좋았나요?\", \"다시 플레이하고 싶나요?\"]")
	private List<String> candidates;
}
