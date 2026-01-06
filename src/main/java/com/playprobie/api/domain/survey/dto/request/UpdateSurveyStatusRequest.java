package com.playprobie.api.domain.survey.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "설문 상태 변경 요청 DTO")
public record UpdateSurveyStatusRequest(

                @Schema(description = "변경할 상태 (ACTIVE, CLOSED)", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "변경할 상태는 필수입니다.") @Pattern(regexp = "ACTIVE|CLOSED", message = "상태는 ACTIVE 또는 CLOSED여야 합니다.") @JsonProperty("status") String status) {
}
