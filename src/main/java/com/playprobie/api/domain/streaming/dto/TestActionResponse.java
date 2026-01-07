package com.playprobie.api.domain.streaming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "테스트 시작/종료 응답 DTO")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestActionResponse(

        @Schema(description = "리소스 상태", example = "ACTIVE") @JsonProperty("status") String status,

        @Schema(description = "현재 용량", example = "5") @JsonProperty("current_capacity") Integer currentCapacity,

        @Schema(description = "응답 메시지", example = "인스턴스 준비 중입니다.") @JsonProperty("message") String message) {

    public static TestActionResponse startTest(String status, Integer currentCapacity) {
        return new TestActionResponse(status, currentCapacity, "인스턴스 준비 중입니다.");
    }

    public static TestActionResponse stopTest(String status, Integer currentCapacity) {
        return new TestActionResponse(status, currentCapacity, null);
    }
}
