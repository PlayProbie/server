package com.playprobie.api.domain.streaming.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WebRTC 시그널링 응답 DTO.
 */
public record SignalResponse(
        @JsonProperty("signal_response") String signalResponse,

        @JsonProperty("survey_session_uuid") UUID surveySessionUuid,

        @JsonProperty("expires_in_seconds") Integer expiresInSeconds) {

    /**
     * 시그널링 응답을 생성합니다.
     * 
     * @param signalResponse    AWS로부터 받은 Signal Answer
     * @param surveySessionUuid DB에 생성된 세션 UUID
     * @return SignalResponse
     */
    public static SignalResponse of(String signalResponse, UUID surveySessionUuid) {
        return new SignalResponse(signalResponse, surveySessionUuid, 120);
    }
}
