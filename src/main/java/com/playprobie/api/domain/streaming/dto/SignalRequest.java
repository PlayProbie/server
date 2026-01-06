package com.playprobie.api.domain.streaming.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "WebRTC 시그널링 요청 DTO")
public record SignalRequest(

                @Schema(description = "GameLift SDK generateSignalRequest() 반환값 (Base64 인코딩)", example = "eyJzaWduYWxSZXF1ZXN0IjoiLi4uIn0=", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "Signal request는 필수입니다.") @JsonProperty("signal_request") String signalRequest) {
}
