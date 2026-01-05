package com.playprobie.api.domain.survey.dto.request;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSurveyRequest(
		@NotNull(message = "게임 ID는 필수입니다") @JsonProperty("game_id") Long gameId,
		@NotBlank(message = "설문 이름은 필수입니다") @JsonProperty("survey_name") String surveyName,
		@NotNull(message = "시작 시간은 필수입니다") @JsonProperty("started_at") OffsetDateTime startedAt,
		@NotNull(message = "종료 시간은 필수입니다") @JsonProperty("ended_at") OffsetDateTime endedAt,
		@NotNull(message = "테스트 목적은 필수입니다") @JsonProperty("test_purpose") String testPurpose,
		// ===== 신규 필드 =====
		@NotNull(message = "테스트 단계는 필수입니다") @JsonProperty("test_stage") String testStage,
		@NotNull(message = "테마 우선순위는 필수입니다") @Size(min = 1, max = 3, message = "테마는 1~3개 선택해야 합니다") @JsonProperty("theme_priorities") List<String> themePriorities,
		@JsonProperty("theme_details") Map<String, List<String>> themeDetails,
		@JsonProperty("version_note") String versionNote) {
}
