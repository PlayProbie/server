package com.playprobie.api.domain.analytics.domain;

/**
 * 설문 분석 상태를 나타내는 도메인 Enum
 *
 * 비즈니스 로직에서 분석 결과의 상태를 표현하며,
 * DTO(AnalyticsResponse)에서도 재사용됨.
 */
public enum AnalysisStatus {
	/** 모든 질문 분석 완료 */
	COMPLETED,

	/** 분석할 데이터 없음 (답변 0개) */
	NO_DATA,

	/** 데이터 부족 (AI가 분석 취소, 답변 < 최소 개수) */
	INSUFFICIENT_DATA
}
