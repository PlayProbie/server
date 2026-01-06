package com.playprobie.api.domain.workspace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "워크스페이스 생성 요청")
public record CreateWorkspaceRequest(
                @Schema(description = "워크스페이스 이름", example = "My Game Studio") @NotBlank(message = "워크스페이스 이름은 필수입니다") @Size(max = 100, message = "워크스페이스 이름은 100자 이하입니다") @JsonProperty("name") String name,

                @Schema(description = "워크스페이스 설명", example = "인디 게임 개발 스튜디오") @Size(max = 500, message = "설명은 500자 이하입니다") @JsonProperty("description") String description) {
}
