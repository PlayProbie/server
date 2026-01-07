package com.playprobie.api.domain.survey.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "고정 질문 생성 요청 DTO")
public record CreateFixedQuestionsRequest(

	@Schema(description = "설문 UUID", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "설문 UUID는 필수입니다") @JsonProperty("survey_uuid")
	UUID surveyUuid,

	@Schema(description = "질문 목록 (1개 이상 필수)", requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty(message = "질문 목록은 필수입니다") @Valid @JsonProperty("questions")
	List<QuestionItem> questions) {

	@Schema(description = "질문 항목")
	public record QuestionItem(

		@Schema(description = "질문 내용", example = "게임 그래픽은 어떠셨나요?", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "질문 내용은 필수입니다") @JsonProperty("q_content")
		String qContent,

		@Schema(description = "질문 순서", example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "질문 순서는 필수입니다") @JsonProperty("q_order")
		Integer qOrder) {
	}
}
