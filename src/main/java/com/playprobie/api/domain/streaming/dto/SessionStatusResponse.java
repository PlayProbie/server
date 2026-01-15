package com.playprobie.api.domain.streaming.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "세션 상태 응답 DTO")
public record SessionStatusResponse(

	@Schema(description = "세션 활성 여부", example = "true") @JsonProperty("is_active")
	Boolean isActive,

	@Schema(description = "설문 세션 UUID", example = "550e8400-e29b-41d4-a716-446655440000") @JsonProperty("survey_session_uuid")
	UUID surveySessionUuid) {

	public static SessionStatusResponse active(UUID surveySessionUuid) {
		return new SessionStatusResponse(true, surveySessionUuid);
	}

	public static SessionStatusResponse inactive() {
		return new SessionStatusResponse(false, null);
	}
}
