package com.playprobie.api.domain.game.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameGenre;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "게임 응답")
public record GameResponse(

                @Schema(description = "게임 UUID", example = "7a3b3c4d-e5f6-7890-abcd-ef1234567890") @JsonProperty("game_uuid") UUID gameUuid,

                @Schema(description = "워크스페이스 UUID", example = "550e8400-e29b-41d4-a716-446655440000") @JsonProperty("workspace_uuid") UUID workspaceUuid,

                @Schema(description = "게임 이름", example = "My RPG Game") @JsonProperty("game_name") String gameName,

                @Schema(description = "게임 장르 코드 배열", example = "[\"RPG\", \"ACTION\"]") @JsonProperty("game_genre") List<String> gameGenre,

                @Schema(description = "게임 설명", example = "중세 판타지 배경의 오픈월드 RPG 게임입니다.") @JsonProperty("game_context") String gameContext,

                @Schema(description = "생성 일시") @JsonProperty("created_at") LocalDateTime createdAt,

                @Schema(description = "수정 일시") @JsonProperty("updated_at") LocalDateTime updatedAt) {
        public static GameResponse from(Game game) {
                List<String> genreCodes = game.getGenres().stream()
                                .map(GameGenre::getCode)
                                .toList();

                UUID workspaceUuid = game.getWorkspace() != null ? game.getWorkspace().getUuid() : null;

                return new GameResponse(
                                game.getUuid(),
                                workspaceUuid,
                                game.getName(),
                                genreCodes,
                                game.getContext(),
                                game.getCreatedAt(),
                                game.getUpdatedAt());
        }
}
