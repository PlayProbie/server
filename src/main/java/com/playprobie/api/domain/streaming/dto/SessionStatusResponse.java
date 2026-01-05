package com.playprobie.api.domain.streaming.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 세션 상태 (Heartbeat) 응답 DTO.
 */
public record SessionStatusResponse(
        @JsonProperty("is_active") Boolean isActive,

        @JsonProperty("survey_session_uuid") UUID surveySessionUuid) {

    /**
     * 활성 상태 응답을 생성합니다.
     */
    public static SessionStatusResponse active(UUID surveySessionUuid) {
        return new SessionStatusResponse(true, surveySessionUuid);
    }

    /**
     * 비활성 상태 응답을 생성합니다.
     */
    public static SessionStatusResponse inactive(UUID surveySessionUuid) {
        return new SessionStatusResponse(false, surveySessionUuid);
    }
}
