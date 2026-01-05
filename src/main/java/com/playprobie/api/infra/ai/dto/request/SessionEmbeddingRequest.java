package com.playprobie.api.infra.ai.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;

// 세션 완료 시 AI 서버에 임베딩 요청을 보내기 위한 DTO
// AI 서버의 InteractionEmbeddingRequest 스키마와 호환
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionEmbeddingRequest(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("survey_id") Long surveyId,
        @JsonProperty("fixed_question_id") Long fixedQuestionId,
        @JsonProperty("qa_pairs") List<QaPair> qaPairs,
        @JsonProperty("metadata") Object metadata) {

    @Builder
    public record QaPair(
            @JsonProperty("question") String question,
            @JsonProperty("answer") String answer,
            @JsonProperty("question_type") String questionType) {
        public static QaPair of(String question, String answer, String questionType) {
            return new QaPair(question, answer, questionType);
        }
    }
}
