package com.playprobie.api.domain.streaming.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 리소스 상태 조회 응답 DTO.
 */
public record ResourceStatusResponse(
        @JsonProperty("status") String status,

        @JsonProperty("current_capacity") Integer currentCapacity,

        @JsonProperty("instances_ready") Boolean instancesReady) {

    /**
     * 상태 응답을 생성합니다.
     * 
     * @param status          리소스 상태
     * @param currentCapacity 현재 용량
     * @param instancesReady  인스턴스 준비 완료 여부
     * @return ResourceStatusResponse
     */
    public static ResourceStatusResponse of(String status, Integer currentCapacity, Boolean instancesReady) {
        return new ResourceStatusResponse(status, currentCapacity, instancesReady);
    }
}
