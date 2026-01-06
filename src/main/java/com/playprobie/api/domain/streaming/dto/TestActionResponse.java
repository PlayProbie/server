package com.playprobie.api.domain.streaming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 테스트 시작/종료 응답 DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestActionResponse(
        @JsonProperty("status") String status,

        @JsonProperty("current_capacity") Integer currentCapacity,

        @JsonProperty("message") String message) {

    /**
     * 테스트 시작 응답을 생성합니다.
     */
    public static TestActionResponse startTest(String status, Integer currentCapacity) {
        return new TestActionResponse(status, currentCapacity, "인스턴스 준비 중입니다.");
    }

    /**
     * 테스트 종료 응답을 생성합니다.
     */
    public static TestActionResponse stopTest(String status, Integer currentCapacity) {
        return new TestActionResponse(status, currentCapacity, null);
    }
}
