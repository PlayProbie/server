package com.playprobie.api.domain.streaming.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "WebRTC 시그널링 응답 DTO")
public record SignalResponse(

        @Schema(description = "AWS로부터 받은 Signal Answer (Base64 인코딩)", example = "eyJzaWduYWxBbnN3ZXIiOiIuLi4ifQ==") @JsonProperty("signal_response") String signalResponse,

        @Schema(description = "생성된 설문 세션 UUID", example = "550e8400-e29b-41d4-a716-446655440000") @JsonProperty("survey_session_uuid") UUID surveySessionUuid,

        @Schema(description = "세션 만료 시간 (초)", example = "120") @JsonProperty("expires_in_seconds") Integer expiresInSeconds) {

    public static SignalResponse of(String signalResponse, UUID surveySessionUuid) {
        return new SignalResponse(signalResponse, surveySessionUuid, 120);
    }
}
