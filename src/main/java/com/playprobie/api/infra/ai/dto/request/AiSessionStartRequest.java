package com.playprobie.api.infra.ai.dto.request;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;

/**
 * AI 서버 세션 시작 요청 DTO (POST /surveys/start-session)
 */
@Getter
@Builder
public class AiSessionStartRequest {

    @JsonProperty("session_id")
    private final String sessionId;

    @JsonProperty("game_info")
    private final Map<String, Object> gameInfo;

    @JsonProperty("tester_profile")
    private final TesterProfileDto testerProfile;

    @Getter
    @Builder
    public static class TesterProfileDto {
        @JsonProperty("tester_id")
        private final String testerId;

        @JsonProperty("age_group")
        private final String ageGroup;

        private final String gender;

        @JsonProperty("prefer_genre")
        private final String preferGenre;

        @JsonProperty("skill_level")
        private final String skillLevel;
    }
}
