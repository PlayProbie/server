package com.playprobie.api.domain.analytics.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Analytics REST API 응답 DTO
 *
 * @param analyses           질문별 분석 결과 리스트
 * @param status             분석 상태 (COMPLETED, NO_DATA, INSUFFICIENT_DATA)
 * @param totalQuestions     전체 질문 수
 * @param completedQuestions 분석 완료된 질문 수
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalyticsResponse(
	List<QuestionResponseAnalysisWrapper> analyses,
	String status,
	int totalQuestions,
	int completedQuestions,
	int totalParticipants,
	String surveySummary) {
}
