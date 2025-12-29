package com.playprobie.api.domain.game.dto;

import com.playprobie.api.domain.game.domain.GameGenre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateGameRequest(
        @NotBlank(message = "게임 이름은 필수입니다") @Size(max = 100, message = "게임 이름은 100자 이하입니다") String name,

        @NotNull(message = "게임 장르는 필수입니다") GameGenre genre,

        @Size(max = 2000, message = "게임 설명은 2000자 이하입니다") String context) {
}
