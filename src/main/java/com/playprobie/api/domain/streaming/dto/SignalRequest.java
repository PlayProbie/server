package com.playprobie.api.domain.streaming.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * WebRTC 시그널링 요청 DTO.
 * 
 * @param signalRequest GameLift SDK generateSignalRequest() 반환값 (Base64)
 */
public record SignalRequest(
        @NotBlank(message = "Signal request는 필수입니다.") @JsonProperty("signal_request") String signalRequest) {
}
