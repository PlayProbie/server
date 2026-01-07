package com.playprobie.api.domain.survey.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "설문 결과 요약 응답 DTO")
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SurveyResultSummaryResponse {

    @Schema(description = "총 설문 수", example = "10")
    private long surveyCount;

    @Schema(description = "총 응답 수", example = "150")
    private long responseCount;

    public static SurveyResultSummaryResponse of(long surveyCount, long responseCount) {
        return SurveyResultSummaryResponse.builder()
                .surveyCount(surveyCount)
                .responseCount(responseCount)
                .build();
    }
}
