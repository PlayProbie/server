package com.playprobie.api.infra.ai.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "세션 임베딩 요청 DTO")
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionEmbeddingRequest(

        @Schema(description = "세션 ID", example = "session_12345") @JsonProperty("session_id") String sessionId,

        @Schema(description = "설문 ID", example = "1") @JsonProperty("survey_id") Long surveyId,

        @Schema(description = "고정 질문 ID", example = "10") @JsonProperty("fixed_question_id") Long fixedQuestionId,

        @Schema(description = "QA 쌍 목록") @JsonProperty("qa_pairs") List<QaPair> qaPairs,

        @Schema(description = "추가 메타데이터") @JsonProperty("metadata") Object metadata) {

    @Schema(description = "질문-답변 쌍")
    @Builder
    public record QaPair(

            @Schema(description = "질문 텍스트", example = "재미있었나요?") @JsonProperty("question") String question,

            @Schema(description = "답변 텍스트", example = "네") @JsonProperty("answer") String answer,

            @Schema(description = "질문 유형 (FIXED, AI)", example = "FIXED") @JsonProperty("question_type") String questionType) {

        public static QaPair of(String question, String answer, String questionType) {
            return new QaPair(question, answer, questionType);
        }
    }
}
