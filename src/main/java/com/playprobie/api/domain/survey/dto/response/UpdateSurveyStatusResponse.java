package com.playprobie.api.domain.survey.dto.response;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.streaming.dto.TestActionResponse;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateSurveyStatusResponse(
        @JsonProperty("survey_uuid") UUID surveyUuid,
        @JsonProperty("status") String status,
        @JsonProperty("streaming_resource") TestActionResponse streamingResource) {
}
