package com.playprobie.api.domain.streaming.dto;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 세션 가용성 응답 DTO.
 * 
 * <p>
 * 테스터가 플레이 화면에 진입했을 때 반환됩니다.
 */
public record SessionAvailabilityResponse(
        @JsonProperty("survey_uuid") UUID surveyUuid,

        @JsonProperty("game_name") String gameName,

        @JsonProperty("is_available") Boolean isAvailable,

        @JsonProperty("stream_settings") Map<String, Object> streamSettings) {

    /**
     * 가용 상태 응답을 생성합니다.
     */
    public static SessionAvailabilityResponse available(UUID surveyUuid, String gameName) {
        return new SessionAvailabilityResponse(
                surveyUuid,
                gameName,
                true,
                Map.of("resolution", "1080p", "fps", 60));
    }

    /**
     * 불가용 상태 응답을 생성합니다.
     */
    public static SessionAvailabilityResponse unavailable(UUID surveyUuid, String gameName) {
        return new SessionAvailabilityResponse(
                surveyUuid,
                gameName,
                false,
                null);
    }
}
