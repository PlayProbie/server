package com.playprobie.api.domain.survey.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.survey.domain.FixedQuestion;

/**
 * 고정 질문 응답 DTO
 * - 설문에 속한 질문 목록 조회 시 반환
 */
public record FixedQuestionResponse(
        @JsonProperty("fixed_q_id") Long fixedQId,

        @JsonProperty("survey_id") Long surveyId,

        @JsonProperty("q_content") String qContent,

        @JsonProperty("q_order") Integer qOrder,

        @JsonProperty("created_at") LocalDateTime createdAt) {
    public static FixedQuestionResponse from(FixedQuestion question) {
        return new FixedQuestionResponse(
                question.getId(),
                question.getSurveyId(),
                question.getContent(),
                question.getOrder(),
                question.getCreatedAt());
    }
}
