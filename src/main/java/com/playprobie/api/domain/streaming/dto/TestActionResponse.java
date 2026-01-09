package com.playprobie.api.domain.streaming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "테스트 API 응답")
public record TestActionResponse(
	@Schema(description = "리소스 상태 (예: SCALING_UP, TESTING)")
	String status,

	@Schema(description = "현재 할당된 Capacity")
	int currentCapacity,

	@Schema(description = "응답 메시지")
	String message,

	@Schema(description = "비동기 요청 추적 ID (SCALING 상태일 때 유효)") @JsonInclude(JsonInclude.Include.NON_NULL)
	Long requestId,

	@Schema(description = "예상 소요 시간(초)") @JsonInclude(JsonInclude.Include.NON_NULL)
	Integer estimatedCompletionSeconds) {
	public static TestActionResponse startTest(String status, int capacity) {
		return new TestActionResponse(status, capacity, "테스트가 시작되었습니다.", null, null);
	}

	public static TestActionResponse stopTest(String status, int capacity) {
		return new TestActionResponse(status, capacity, "테스트가 종료되었습니다.", null, null);
	}

	public static TestActionResponse inProgress(String status, int capacity, Long requestId) {
		return new TestActionResponse(status, capacity, "요청이 비동기로 처리 중입니다.", requestId, 5);
	}

	public boolean isAsyncPending() {
		return "SCALING_UP".equals(status) || "SCALING_DOWN".equals(status);
	}
}
