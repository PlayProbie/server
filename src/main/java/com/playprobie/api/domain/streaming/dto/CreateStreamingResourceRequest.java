package com.playprobie.api.domain.streaming.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "스트리밍 리소스 생성 요청 DTO")
public record CreateStreamingResourceRequest(

	@Schema(description = "연결할 빌드 UUID", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "빌드 ID는 필수입니다.") @JsonProperty("build_uuid")
	UUID buildId,

	@Schema(description = "Stream Class ID (예: gen4n_win2022)", example = "gen4n_win2022", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "인스턴스 타입은 필수입니다.") @JsonProperty("instance_type")
	String instanceType,

	@Schema(description = "서비스 시 목표 동시 접속자 수 (최소 1)", example = "10", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "최대 용량은 필수입니다.") @Min(value = 1, message = "최대 용량은 1 이상이어야 합니다.") @JsonProperty("max_capacity")
	Integer maxCapacity) {
}
