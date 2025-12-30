package com.playprobie.api.domain.survey.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 고정 질문 생성 응답 DTO
 * POST /surveys/fixed_questions 응답
 */
public record FixedQuestionsCountResponse(
        @JsonProperty("count") int count) {

    public static FixedQuestionsCountResponse of(int count) {
        return new FixedQuestionsCountResponse(count);
    }
}
