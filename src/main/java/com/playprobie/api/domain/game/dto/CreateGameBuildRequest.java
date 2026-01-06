package com.playprobie.api.domain.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "게임 빌드 생성 요청 DTO")
public record CreateGameBuildRequest(

                @Schema(description = "빌드 버전명", example = "1.0.0", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "버전명은 필수입니다.") String version) {
}
