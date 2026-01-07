package com.playprobie.api.domain.streaming.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "세션 종료 응답 DTO")
public record TerminateSessionResponse(

        @Schema(description = "종료 성공 여부", example = "true") @JsonProperty("success") boolean success) {

    public static TerminateSessionResponse ok() {
        return new TerminateSessionResponse(true);
    }
}
