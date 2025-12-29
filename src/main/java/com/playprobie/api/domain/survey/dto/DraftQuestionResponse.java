package com.playprobie.api.domain.survey.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.survey.domain.DraftQuestion;

/**
 * 임시 질문 응답 DTO
 */
public record DraftQuestionResponse( // 임시 질문 응답 DTO (record: 불변 객체)
        @JsonProperty("draft_q_id") Long draftQId,

        @JsonProperty("survey_id") Long surveyId,

        @JsonProperty("q_content") String qContent,

        @JsonProperty("q_order") Integer qOrder,

        @JsonProperty("created_at") LocalDateTime createdAt) {
    public static DraftQuestionResponse from(DraftQuestion question) { // 임시 질문 응답 DTO로 변환
        return new DraftQuestionResponse(
                question.getId(),
                question.getSurveyId(),
                question.getContent(),
                question.getOrder(),
                question.getCreatedAt());
    }
}
