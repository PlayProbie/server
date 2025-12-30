package com.playprobie.api.domain.survey.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SurveyResultSummaryResponse {

    private long surveyCount;
    private long responseCount;

    public static SurveyResultSummaryResponse of(long surveyCount, long responseCount) {
        return SurveyResultSummaryResponse.builder()
                .surveyCount(surveyCount)
                .responseCount(responseCount)
                .build();
    }
}
