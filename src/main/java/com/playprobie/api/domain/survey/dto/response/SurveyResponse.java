package com.playprobie.api.domain.survey.dto.response;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.survey.domain.Survey;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "설문 응답 DTO")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SurveyResponse(

		@Schema(description = "설문 UUID", example = "550e8400-e29b-41d4-a716-446655440000") @JsonProperty("survey_uuid") UUID surveyUuid,

		@Schema(description = "설문 이름", example = "출시 전 테스트 설문") @JsonProperty("survey_name") String surveyName,

		@Schema(description = "설문 상태 (DRAFT, ACTIVE, CLOSED)", example = "ACTIVE") @JsonProperty("status") String status,

		@Schema(description = "설문 URL", example = "https://playprobie.com/surveys/abc123") @JsonProperty("survey_url") String surveyUrl,

		@Schema(description = "설문 시작 시간", example = "2024-01-01T09:00:00+09:00", type = "string") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") @JsonProperty("started_at") OffsetDateTime startedAt,

		@Schema(description = "설문 종료 시간", example = "2024-01-31T18:00:00+09:00", type = "string") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") @JsonProperty("ended_at") OffsetDateTime endedAt,

		@Schema(description = "테스트 목적 코드", example = "BALANCE_TEST") @JsonProperty("test_purpose") String testPurpose,

		@Schema(description = "생성 일시", example = "2024-01-01T09:00:00+09:00", type = "string") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") @JsonProperty("created_at") OffsetDateTime createdAt) {

	public static SurveyResponse from(Survey survey) {
		return new SurveyResponse(
				survey.getUuid(),
				survey.getName(),
				survey.getStatus().name(),
				survey.getSurveyUrl(),
				survey.getStartAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
				survey.getEndAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
				survey.getTestPurpose() != null ? survey.getTestPurpose().getCode() : null,
				survey.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
	}

	public static SurveyResponse forList(Survey survey) {
		return new SurveyResponse(
				survey.getUuid(),
				survey.getName(),
				survey.getStatus().name(),
				null,
				null,
				null,
				null,
				survey.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
	}
}
