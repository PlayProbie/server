package com.playprobie.api.domain.survey.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 질문 리뷰 응답 DTO
 * - AI가 제공하는 피드백 + 대안 3개
 */
public record QuestionReviewResponse(
        @JsonProperty("draft_q_id") Long draftQId,

        @JsonProperty("original_content") String originalContent,

        @JsonProperty("feedback") String feedback,

        @JsonProperty("alternatives") List<String> alternatives) {
}
