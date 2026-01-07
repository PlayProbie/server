package com.playprobie.api.infra.ai.dto.request;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;

/**
 * AI 서버 세션 종료 요청 DTO (POST /surveys/end-session)
 */
@Getter
@Builder
public class AiSessionEndRequest {

    @JsonProperty("session_id")
    private final String sessionId;

    @JsonProperty("end_reason")
    private final String endReason;

    @JsonProperty("game_info")
    private final Map<String, Object> gameInfo;
}
