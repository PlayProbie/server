package com.playprobie.api.domain.streaming.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "리소스 상태 조회 응답 DTO")
public record ResourceStatusResponse(

        @Schema(description = "리소스 상태 (CREATING, READY, ACTIVE, DELETING, DELETED, FAILED)", example = "READY") @JsonProperty("status") String status,

        @Schema(description = "현재 용량 (활성 세션 수)", example = "5") @JsonProperty("current_capacity") Integer currentCapacity,

        @Schema(description = "인스턴스 준비 완료 여부", example = "true") @JsonProperty("instances_ready") Boolean instancesReady) {

    public static ResourceStatusResponse of(String status, Integer currentCapacity, Boolean instancesReady) {
        return new ResourceStatusResponse(status, currentCapacity, instancesReady);
    }
}
