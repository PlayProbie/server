package com.playprobie.api.domain.streaming.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

/**
 * 세션 종료 요청 DTO.
 * 
 * @param surveySessionUuid 종료할 세션 UUID
 * @param reason            종료 사유 (user_exit, timeout, error)
 */
public record TerminateSessionRequest(
        @NotNull(message = "세션 UUID는 필수입니다.") @JsonProperty("survey_session_uuid") UUID surveySessionUuid,

        @JsonProperty("reason") String reason) {
}
