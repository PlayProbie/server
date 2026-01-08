package com.playprobie.api.infra.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "질문 분석 요청 DTO")
@Builder
public record QuestionAnalysisRequest(

	@Schema(description = "설문 UUID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") @JsonProperty("survey_uuid")
	String surveyUuid,

	@Schema(description = "고정 질문 ID", example = "10") @JsonProperty("fixed_question_id")
	Long fixedQuestionId) {
}
