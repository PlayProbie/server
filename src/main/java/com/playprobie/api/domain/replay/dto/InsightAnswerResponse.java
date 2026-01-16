package com.playprobie.api.domain.replay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 인사이트 질문 답변 Response
 * 다음 질문은 SSE로 전송되므로 REST 응답에는 완료 여부만 포함
 */
public record InsightAnswerResponse(
	@JsonProperty("tag_id")
	Long tagId,

	@JsonProperty("is_complete")
	Boolean isComplete) {
	/**
	 * 완료 응답 생성 (모든 질문 완료)
	 */
	public static InsightAnswerResponse complete(Long tagId) {
		return new InsightAnswerResponse(tagId, true);
	}

	/**
	 * 진행 중 응답 생성 (다음 질문은 SSE로 전송)
	 */
	public static InsightAnswerResponse inProgress(Long tagId) {
		return new InsightAnswerResponse(tagId, false);
	}
}
