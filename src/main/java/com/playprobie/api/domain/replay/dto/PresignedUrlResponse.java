package com.playprobie.api.domain.replay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Presigned URL 발급 Response
 */
public record PresignedUrlResponse(
	@JsonProperty("segment_id")
	String segmentId,

	@JsonProperty("s3_url")
	String s3Url,

	@JsonProperty("expires_in")
	Integer expiresIn) {
}
