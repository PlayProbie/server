package com.playprobie.api.domain.replay.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 입력 로그 배치 수신 Request
 * POST /sessions/{sessionId}/replay/logs
 */
public record ReplayLogRequest(
	@NotBlank @JsonProperty("session_id")
	String sessionId,

	@NotBlank @JsonProperty("segment_id")
	String segmentId,

	@NotBlank @JsonProperty("video_url")
	String videoUrl,

	@NotNull @Valid
	List<InputLogDto> logs) {
}
