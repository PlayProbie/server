package com.playprobie.api.domain.survey.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.streaming.dto.TestActionResponse;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "설문 상태 변경 응답 DTO")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateSurveyStatusResponse(

	@Schema(description = "설문 UUID", example = "550e8400-e29b-41d4-a716-446655440000") @JsonProperty("survey_uuid")
	UUID surveyUuid,

	@Schema(description = "변경된 상태 (ACTIVE, CLOSED)", example = "ACTIVE") @JsonProperty("status")
	String status,

	@Schema(description = "스트리밍 리소스 상태 (ACTIVE 상태로 변경 시 포함)") @JsonProperty("streaming_resource") @JsonInclude(JsonInclude.Include.ALWAYS)
	TestActionResponse streamingResource) {
}
