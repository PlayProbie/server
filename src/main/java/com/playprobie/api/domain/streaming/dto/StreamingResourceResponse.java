package com.playprobie.api.domain.streaming.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.streaming.domain.StreamingConstants;
import com.playprobie.api.domain.streaming.domain.StreamingResource;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "스트리밍 리소스 응답 DTO")
public record StreamingResourceResponse(

	@Schema(description = "리소스 UUID", example = "550e8400-e29b-41d4-a716-446655440000") @JsonProperty("uuid")
	String id,

	@Schema(description = "리소스 상태 (CREATING, READY, ACTIVE, DELETING, DELETED, FAILED)", example = "READY") @JsonProperty("status")
	String status,

	@Schema(description = "현재 용량 (활성 세션 수)", example = "5") @JsonProperty("current_capacity")
	Integer currentCapacity,

	@Schema(description = "최대 용량", example = "10") @JsonProperty("max_capacity")
	Integer maxCapacity,

	@Schema(description = "인스턴스 타입", example = "gen4n_win2022") @JsonProperty("instance_type")
	String instanceType,

	@Schema(description = "생성 일시", example = "2024-01-01T09:00:00+09:00", type = "string") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") @JsonProperty("created_at")
	OffsetDateTime createdAt) {

	public static StreamingResourceResponse from(StreamingResource resource) {
		return new StreamingResourceResponse(
			resource.getUuid().toString(),
			resource.getStatus().name(),
			resource.getCurrentCapacity(),
			resource.getMaxCapacity(),
			resource.getInstanceType(),
			resource.getCreatedAt().atZone(StreamingConstants.DEFAULT_TIMEZONE).toOffsetDateTime());
	}
}
