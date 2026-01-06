package com.playprobie.api.domain.survey.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateSurveyStatusRequest(
        @NotBlank(message = "변경할 상태는 필수입니다.") @Pattern(regexp = "ACTIVE|CLOSED", message = "상태는 ACTIVE 또는 CLOSED여야 합니다.") @JsonProperty("status") String status) {
}
