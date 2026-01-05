package com.playprobie.api.domain.streaming.dto;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SessionStatusResponse(
        @JsonProperty("is_active") Boolean isActive,
        @JsonProperty("survey_session_uuid") UUID surveySessionUuid) {

    public static SessionStatusResponse active(UUID surveySessionUuid) {
        return new SessionStatusResponse(true, surveySessionUuid);
    }

    public static SessionStatusResponse inactive() {
        return new SessionStatusResponse(false, null);
    }
}
