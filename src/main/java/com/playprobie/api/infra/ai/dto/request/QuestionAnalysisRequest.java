package com.playprobie.api.infra.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;

@Builder
public record QuestionAnalysisRequest(
        @JsonProperty("survey_id") Long surveyId,
        @JsonProperty("fixed_question_id") Long fixedQuestionId) {
}
