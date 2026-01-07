package com.playprobie.api.domain.analytics.dto;

import java.util.Objects;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Builder;

@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record QuestionResponseAnalysisWrapper(
        Long fixedQuestionId,
        String resultJson) {

    public QuestionResponseAnalysisWrapper {
        Objects.requireNonNull(fixedQuestionId, "fixedQuestionId는 필수입니다");
        Objects.requireNonNull(resultJson, "resultJson은 필수입니다");
    }
}
