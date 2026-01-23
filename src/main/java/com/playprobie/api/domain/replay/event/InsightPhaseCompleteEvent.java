package com.playprobie.api.domain.replay.event;

/**
 * 인사이트 질문 Phase 완료 이벤트
 * 모든 인사이트 질문 완료 시 발행되어 클로징 트리거
 */
public record InsightPhaseCompleteEvent(
	String sessionUuid) {
}
