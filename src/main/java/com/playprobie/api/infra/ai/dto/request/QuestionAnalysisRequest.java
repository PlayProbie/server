package com.playprobie.api.infra.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "질문 분석 요청 DTO")
@Builder
public record QuestionAnalysisRequest(

                @Schema(description = "설문 ID", example = "1") @JsonProperty("survey_id") Long surveyId,

                @Schema(description = "고정 질문 ID", example = "10") @JsonProperty("fixed_question_id") Long fixedQuestionId) {
}
