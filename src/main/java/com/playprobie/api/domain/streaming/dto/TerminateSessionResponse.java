package com.playprobie.api.domain.streaming.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 세션 종료 응답 DTO.
 */
public record TerminateSessionResponse(
        @JsonProperty("success") boolean success) {

    /**
     * 성공 응답을 생성합니다.
     */
    public static TerminateSessionResponse ok() {
        return new TerminateSessionResponse(true);
    }
}
