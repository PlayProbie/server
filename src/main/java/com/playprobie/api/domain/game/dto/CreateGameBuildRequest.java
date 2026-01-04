package com.playprobie.api.domain.game.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateGameBuildRequest(
        @NotBlank(message = "버전명은 필수입니다.") String version) {
}
