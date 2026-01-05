package com.playprobie.api.domain.survey.dto.response;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.survey.domain.Survey;

public record SurveyResponse(
		@JsonProperty("survey_uuid") java.util.UUID surveyUuid,
		@JsonProperty("survey_name") String surveyName,
		@JsonProperty("survey_url") String surveyUrl,
		@JsonProperty("started_at") OffsetDateTime startedAt,
		@JsonProperty("ended_at") OffsetDateTime endedAt,
		@JsonProperty("test_purpose") String testPurpose,
		@JsonProperty("created_at") OffsetDateTime createdAt) {
	public static SurveyResponse from(Survey survey) {
		return new SurveyResponse(
				survey.getUuid(),
				survey.getName(),
				survey.getSurveyUrl(),
				survey.getStartAt().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
				survey.getEndAt().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
				survey.getTestPurpose() != null ? survey.getTestPurpose().getCode() : null,
				survey.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());
	}
}
