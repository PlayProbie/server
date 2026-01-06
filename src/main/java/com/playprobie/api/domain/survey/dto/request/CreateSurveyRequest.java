package com.playprobie.api.domain.survey.dto.request;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "설문 생성 요청 DTO")
public record CreateSurveyRequest(

		@Schema(description = "게임 UUID", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "게임 UUID는 필수입니다") @JsonProperty("game_uuid") UUID gameUuid,

		@Schema(description = "설문 이름", example = "출시 전 테스트 설문", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "설문 이름은 필수입니다") @JsonProperty("survey_name") String surveyName,

		@Schema(description = "설문 시작 시간 (ISO 8601 형식)", example = "2024-01-01T09:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED, type = "string") @NotNull(message = "시작 시간은 필수입니다") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") @JsonProperty("started_at") OffsetDateTime startedAt,

		@Schema(description = "설문 종료 시간 (ISO 8601 형식)", example = "2024-01-31T18:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED, type = "string") @NotNull(message = "종료 시간은 필수입니다") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") @JsonProperty("ended_at") OffsetDateTime endedAt,

		@Schema(description = "테스트 목적", example = "전투 밸런스 피드백 수집", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "테스트 목적은 필수입니다") @JsonProperty("test_purpose") String testPurpose) {
}
