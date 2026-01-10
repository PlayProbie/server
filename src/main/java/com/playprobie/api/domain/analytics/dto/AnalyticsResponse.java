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
        int completedQuestions) {
    /**
     * 분석 상태 (3가지)
     * - IN_PROGRESS는 클라이언트에 노출하지 않음 (완료된 데이터만 반환)
     */
    public enum Status {
        COMPLETED, // 모든 질문 분석 완료
        NO_DATA, // 분석할 데이터 없음 (답변 0개)
        INSUFFICIENT_DATA // 데이터 부족 (AI가 분석 취소, 답변 < 최소 개수)
    }

    public static AnalyticsResponse empty() {
        return new AnalyticsResponse(List.of(), Status.NO_DATA.name(), 0, 0);
    }

    public static AnalyticsResponse of(
            List<QuestionResponseAnalysisWrapper> analyses,
            int totalQuestions) {
        if (analyses.isEmpty()) {
            return new AnalyticsResponse(analyses, Status.NO_DATA.name(), totalQuestions, 0);
        }

        // 완료된 분석 개수가 전체 질문 수와 같으면 COMPLETED
        if (analyses.size() >= totalQuestions) {
            return new AnalyticsResponse(analyses, Status.COMPLETED.name(), totalQuestions, analyses.size());
        } else {
            // 일부만 완료된 경우 (데이터 부족으로 일부 질문 분석 실패)
            return new AnalyticsResponse(analyses, Status.INSUFFICIENT_DATA.name(), totalQuestions, analyses.size());
        }
    }
}
