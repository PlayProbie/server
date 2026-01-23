package com.playprobie.api.domain.replay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * 업로드 완료 알림 Request
 * POST /sessions/{sessionId}/replay/upload-complete
 */
public record UploadCompleteRequest(
	@NotBlank @JsonProperty("segment_id")
	String segmentId) {
}
