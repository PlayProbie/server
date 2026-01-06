package com.playprobie.api.domain.survey.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "질문 리뷰 응답 DTO (AI 피드백 + 대안 제시)")
public record QuestionReviewResponse(

		@Schema(description = "초안 질문 ID", example = "1") @JsonProperty("draft_q_id") Long draftQId,

		@Schema(description = "원본 질문 내용", example = "게임 그래픽은 어떠셨나요?") @JsonProperty("original_content") String originalContent,

		@Schema(description = "AI 피드백", example = "질문이 명확합니다. 좋습니다!") @JsonProperty("feedback") String feedback,

		@Schema(description = "대체 질문 제안 목록 (3개)") @JsonProperty("alternatives") List<String> alternatives) {
}
