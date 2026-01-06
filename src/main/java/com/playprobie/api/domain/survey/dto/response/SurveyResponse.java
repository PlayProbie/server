package com.playprobie.api.domain.survey.dto.response;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.survey.domain.Survey;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SurveyResponse(
		@JsonProperty("survey_uuid") java.util.UUID surveyUuid,
		@JsonProperty("survey_name") String surveyName,
		@JsonProperty("status") String status,
		@JsonProperty("survey_url") String surveyUrl,
		@JsonProperty("started_at") OffsetDateTime startedAt,
		@JsonProperty("ended_at") OffsetDateTime endedAt,
		@JsonProperty("test_purpose") String testPurpose,
		@JsonProperty("created_at") OffsetDateTime createdAt,
		// 신규 필드 (feat/#47)
		@JsonProperty("test_stage") String testStage,
		@JsonProperty("theme_priorities") List<String> themePriorities,
		@JsonProperty("theme_details") Map<String, List<String>> themeDetails,
		@JsonProperty("version_note") String versionNote) {

	public static SurveyResponse from(Survey survey) {
		return new SurveyResponse(
				survey.getUuid(),
				survey.getName(),
				survey.getStatus().name(),
				survey.getSurveyUrl(),
				survey.getStartAt() != null ? survey.getStartAt().atZone(ZoneId.systemDefault()).toOffsetDateTime()
						: null,
				survey.getEndAt() != null ? survey.getEndAt().atZone(ZoneId.systemDefault()).toOffsetDateTime() : null,
				survey.getTestPurpose() != null ? survey.getTestPurpose().getCode() : null,
				survey.getCreatedAt() != null ? survey.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime()
						: null,
				// 신규 필드
				survey.getTestStage() != null ? survey.getTestStage().getCode() : null,
				survey.getThemePriorities(),
				survey.getThemeDetails(),
				survey.getVersionNote());
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
				survey.getCreatedAt() != null ? survey.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime()
						: null,
				// 신규 필드
				survey.getTestStage() != null ? survey.getTestStage().getCode() : null,
				null,
				null,
				null);
	}
}
