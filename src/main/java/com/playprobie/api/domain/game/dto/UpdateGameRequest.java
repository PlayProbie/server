package com.playprobie.api.domain.game.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Schema(description = "게임 수정 요청 DTO")
public record UpdateGameRequest(

        @Schema(description = "게임 이름 (최대 100자)", example = "Updated RPG Game", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "게임 이름은 필수입니다") @Size(max = 100, message = "게임 이름은 100자 이하입니다") @JsonProperty("game_name") String gameName,

        @Schema(description = "게임 장르 코드 배열 (1개 이상 필수)", example = "[\"RPG\", \"ADVENTURE\"]", requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty(message = "게임 장르는 필수입니다") @JsonProperty("game_genre") List<String> gameGenre,

        @Schema(description = "게임 설명 (최대 2000자, 선택 사항)", example = "수정된 게임 설명입니다.") @Size(max = 2000, message = "게임 설명은 2000자 이하입니다") @JsonProperty("game_context") String gameContext) {
}
