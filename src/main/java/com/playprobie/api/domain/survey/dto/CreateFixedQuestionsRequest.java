package com.playprobie.api.domain.survey.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 고정 질문 생성 요청 DTO
 * POST /surveys/fixed_questions
 */
public record CreateFixedQuestionsRequest(
        @NotNull(message = "설문 ID는 필수입니다") @JsonProperty("survey_id") Long surveyId,

        @NotEmpty(message = "질문 목록은 필수입니다") @Valid @JsonProperty("questions") List<QuestionItem> questions) {

    public record QuestionItem(
            @NotNull(message = "질문 내용은 필수입니다") @JsonProperty("q_content") String qContent,

            @NotNull(message = "질문 순서는 필수입니다") @JsonProperty("q_order") Integer qOrder) {
    }
}
