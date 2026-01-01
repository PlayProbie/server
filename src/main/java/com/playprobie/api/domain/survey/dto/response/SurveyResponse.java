package com.playprobie.api.domain.survey.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.survey.domain.Survey;

public record SurveyResponse(
		@JsonProperty("survey_id") Long surveyId,

		@JsonProperty("survey_name") String surveyName,

		@JsonProperty("survey_url") String surveyUrl,

		@JsonProperty("started_at") LocalDateTime startedAt,

		@JsonProperty("ended_at") LocalDateTime endedAt,

		@JsonProperty("test_purpose") String testPurpose,

		@JsonProperty("created_at") LocalDateTime createdAt) {
	public static SurveyResponse from(Survey survey) {
		return new SurveyResponse(
				survey.getId(),
				survey.getName(),
				survey.getSurveyUrl(),
				survey.getStartAt(),
				survey.getEndAt(),
				survey.getTestPurpose() != null ? survey.getTestPurpose().getCode() : null,
				survey.getCreatedAt());
	}
}
