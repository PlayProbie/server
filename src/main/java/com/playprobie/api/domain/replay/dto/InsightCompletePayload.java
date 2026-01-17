package com.playprobie.api.domain.replay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 인사이트 완료 SSE 이벤트 Payload
 * event: "insight_complete"
 */
public record InsightCompletePayload(
	@JsonProperty("total_insights")
	Integer totalInsights,

	@JsonProperty("answered")
	Integer answered) {
}
