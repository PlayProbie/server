package com.playprobie.api.domain.game.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Schema(description = "게임 생성 요청")
public record CreateGameRequest(
        @Schema(description = "게임 이름", example = "My RPG Game") @NotBlank(message = "게임 이름은 필수입니다") @Size(max = 100, message = "게임 이름은 100자 이하입니다") @JsonProperty("game_name") String gameName,

        @Schema(description = "게임 장르 코드 배열", example = "[\"RPG\", \"ACTION\"]") @NotEmpty(message = "게임 장르는 필수입니다") @JsonProperty("game_genre") List<String> gameGenre,

        @Schema(description = "게임 설명", example = "중세 판타지 배경의 오픈월드 RPG 게임입니다.") @Size(max = 2000, message = "게임 설명은 2000자 이하입니다") @JsonProperty("game_context") String gameContext) {
}
