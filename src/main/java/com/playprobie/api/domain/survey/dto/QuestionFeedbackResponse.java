package com.playprobie.api.domain.survey.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "질문 피드백 응답 항목 DTO")
public record QuestionFeedbackResponse(

	@Schema(description = "질문 내용", example = "게임 난이도는 적절한가요?") @JsonProperty("question")
	String question,

	@Schema(description = "AI 피드백", example = "질문이 너무 일반적입니다. 더 구체적인 맥락을 추가하는 것이 좋겠습니다.") @JsonProperty("ai_feedback")
	String aiFeedback,

	@Schema(description = "대체 질문 제안 목록") @JsonProperty("suggestions")
	List<String> suggestions) {
}
