package com.playprobie.api.domain.streaming.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "세션 종료 요청 DTO")
public record TerminateSessionRequest(

	@Schema(description = "종료할 세션 UUID", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "세션 UUID는 필수입니다.") @JsonProperty("survey_session_uuid")
	UUID surveySessionUuid,

	@Schema(description = "종료 사유 (user_exit, timeout, error)", example = "user_exit") @JsonProperty("reason")
	String reason) {
}
