package com.playprobie.api.infra.ai.dto.response;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "AI 질문 생성 응답 DTO")
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GenerateQuestionResponse {

	@Schema(description = "생성된 질문 목록", example = "[\"이 게임의 가장 큰 장점은 무엇인가요?\", \"조작감은 불편하지 않았나요?\"]")
	private List<String> questions;
}
