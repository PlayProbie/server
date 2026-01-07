package com.playprobie.api.domain.survey.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "질문 수정 요청 DTO")
public record UpdateQuestionRequest(

		@Schema(description = "수정할 질문 내용", example = "게임 그래픽은 어떠셨나요?", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "질문 내용은 필수입니다") @JsonProperty("q_content") String qContent) {
}
