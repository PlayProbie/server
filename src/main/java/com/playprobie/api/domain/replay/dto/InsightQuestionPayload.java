package com.playprobie.api.domain.replay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.replay.domain.InsightType;

/**
 * 인사이트 질문 SSE 이벤트 Payload
 * event: "insight_question"
 */
public record InsightQuestionPayload(
	@JsonProperty("tag_id")
	Long tagId,

	@JsonProperty("insight_type")
	InsightType insightType,

	@JsonProperty("video_start_ms")
	Long videoStartMs,

	@JsonProperty("video_end_ms")
	Long videoEndMs,

	@JsonProperty("question_text")
	String questionText,

	@JsonProperty("turn_num")
	Integer turnNum,

	@JsonProperty("remaining_insights")
	Integer remainingInsights) {
}
