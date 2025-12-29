package com.playprobie.api.domain.game.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateGameRequest(
                @NotBlank(message = "게임 이름은 필수입니다") @Size(max = 100, message = "게임 이름은 100자 이하입니다") @JsonProperty("game_name") String gameName,

                @NotEmpty(message = "게임 장르는 필수입니다") @JsonProperty("game_genre") List<String> gameGenre,

                @Size(max = 2000, message = "게임 설명은 2000자 이하입니다") @JsonProperty("game_context") String gameContext) {
}
