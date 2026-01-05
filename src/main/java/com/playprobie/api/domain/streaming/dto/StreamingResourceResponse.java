package com.playprobie.api.domain.streaming.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.streaming.domain.StreamingResource;

/**
 * 스트리밍 리소스 응답 DTO.
 */
public record StreamingResourceResponse(
        @JsonProperty("id") String id,

        @JsonProperty("status") String status,

        @JsonProperty("current_capacity") Integer currentCapacity,

        @JsonProperty("max_capacity") Integer maxCapacity,

        @JsonProperty("instance_type") String instanceType,

        @JsonProperty("created_at") LocalDateTime createdAt) {

    /**
     * StreamingResource 엔티티로부터 응답 DTO를 생성합니다.
     * 
     * @param resource StreamingResource 엔티티
     * @return StreamingResourceResponse
     */
    public static StreamingResourceResponse from(StreamingResource resource) {
        return new StreamingResourceResponse(
                resource.getUuid().toString(),
                resource.getStatus().name(),
                resource.getCurrentCapacity(),
                resource.getMaxCapacity(),
                resource.getInstanceType(),
                resource.getCreatedAt());
    }
}
