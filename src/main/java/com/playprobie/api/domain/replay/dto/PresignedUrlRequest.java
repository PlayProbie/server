package com.playprobie.api.domain.replay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Presigned URL 발급 Request
 * POST /sessions/{sessionId}/replay/presigned-url
 */
public record PresignedUrlRequest(
	@NotNull
	Integer sequence,

	@NotNull @JsonProperty("video_start_ms")
	Long videoStartMs,

	@NotNull @JsonProperty("video_end_ms")
	Long videoEndMs,

	@NotBlank @JsonProperty("content_type")
	String contentType) {
}
