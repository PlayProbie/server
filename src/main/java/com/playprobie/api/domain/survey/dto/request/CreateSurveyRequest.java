package com.playprobie.api.domain.survey.dto.request;

import java.time.OffsetDateTime;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSurveyRequest(
		@NotNull(message = "게임 UUID는 필수입니다") @JsonProperty("game_uuid") UUID gameUuid,
		@NotBlank(message = "설문 이름은 필수입니다") @JsonProperty("survey_name") String surveyName,
		@NotNull(message = "시작 시간은 필수입니다") @JsonProperty("started_at") OffsetDateTime startedAt,
		@NotNull(message = "종료 시간은 필수입니다") @JsonProperty("ended_at") OffsetDateTime endedAt,
		@NotNull(message = "테스트 목적은 필수입니다") @JsonProperty("test_purpose") String testPurpose) {
}
