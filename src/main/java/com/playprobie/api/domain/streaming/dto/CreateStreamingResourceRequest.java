package com.playprobie.api.domain.streaming.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 스트리밍 리소스 생성 요청 DTO.
 * 
 * @param buildId      연결할 빌드 UUID
 * @param instanceType Stream Class ID (예: "gen4n_win2022")
 * @param maxCapacity  서비스 시 목표 동시 접속자 수
 */
public record CreateStreamingResourceRequest(
                @NotNull(message = "빌드 ID는 필수입니다.") @JsonProperty("build_uuid") UUID buildId,

                @NotBlank(message = "인스턴스 타입은 필수입니다.") @JsonProperty("instance_type") String instanceType,

                @NotNull(message = "최대 용량은 필수입니다.") @Min(value = 1, message = "최대 용량은 1 이상이어야 합니다.") @JsonProperty("max_capacity") Integer maxCapacity) {
}
