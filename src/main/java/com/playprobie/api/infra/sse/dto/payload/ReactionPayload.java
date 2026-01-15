package com.playprobie.api.infra.sse.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;

/**
 * AI 반응(칭찬, 공감 등) SSE 이벤트 Payload
 */
@Getter
@Builder
public class ReactionPayload {
	@JsonProperty("reaction_text")
	private String reactionText;
}
