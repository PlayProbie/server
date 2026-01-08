package com.playprobie.api.domain.survey.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.survey.domain.FixedQuestion;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "고정 질문 응답 DTO")
public record FixedQuestionResponse(

	@Schema(description = "고정 질문 ID", example = "1") @JsonProperty("fixed_q_id")
	Long fixedQId,

	@Schema(description = "설문 ID", example = "1") @JsonProperty("survey_id")
	Long surveyId,

	@Schema(description = "질문 내용", example = "게임 그래픽은 어떠셨나요?") @JsonProperty("q_content")
	String qContent,

	@Schema(description = "질문 순서", example = "1") @JsonProperty("q_order")
	Integer qOrder,

	@Schema(description = "생성 일시", example = "2024-01-01T09:00:00+09:00", type = "string") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") @JsonProperty("created_at")
	OffsetDateTime createdAt) {

	public static FixedQuestionResponse from(FixedQuestion question) {
		return new FixedQuestionResponse(
			question.getId(),
			question.getSurveyId(),
			question.getContent(),
			question.getOrder(),
			question.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
	}
}
