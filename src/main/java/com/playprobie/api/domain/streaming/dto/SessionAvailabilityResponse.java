package com.playprobie.api.domain.streaming.dto;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "세션 가용성 응답 DTO (테스터가 플레이 화면 진입 시 반환)")
public record SessionAvailabilityResponse(

        @Schema(description = "설문 UUID", example = "550e8400-e29b-41d4-a716-446655440000") @JsonProperty("survey_uuid") UUID surveyUuid,

        @Schema(description = "게임 이름", example = "My RPG Game") @JsonProperty("game_name") String gameName,

        @Schema(description = "세션 가용 여부", example = "true") @JsonProperty("is_available") Boolean isAvailable,

        @Schema(description = "스트림 설정 (해상도, FPS 등)") @JsonProperty("stream_settings") Map<String, Object> streamSettings) {

    public static SessionAvailabilityResponse available(UUID surveyUuid, String gameName) {
        return new SessionAvailabilityResponse(
                surveyUuid,
                gameName,
                true,
                Map.of("resolution", "1080p", "fps", 60));
    }

    public static SessionAvailabilityResponse unavailable(UUID surveyUuid, String gameName) {
        return new SessionAvailabilityResponse(
                surveyUuid,
                gameName,
                false,
                null);
    }
}
