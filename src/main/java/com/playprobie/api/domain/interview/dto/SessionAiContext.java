package com.playprobie.api.domain.interview.dto;

import java.util.Map;

import com.playprobie.api.infra.ai.dto.request.AiSessionStartRequest;

import lombok.Builder;

@Builder
public record SessionAiContext(
	Map<String, Object> gameInfo,
	AiSessionStartRequest.TesterProfileDto testerProfile) {
}
