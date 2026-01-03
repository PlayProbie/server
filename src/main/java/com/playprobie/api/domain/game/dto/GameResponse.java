package com.playprobie.api.domain.game.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameGenre;

public record GameResponse(

                @JsonProperty("game_uuid") UUID gameUuid,

                @JsonProperty("game_name") String gameName,

                @JsonProperty("game_genre") List<String> gameGenre,

                @JsonProperty("game_context") String gameContext,

                @JsonProperty("created_at") LocalDateTime createdAt) {
        public static GameResponse from(Game game) {
                List<String> genreCodes = game.getGenres().stream()
                                .map(GameGenre::getCode)
                                .toList();

                return new GameResponse(
                                game.getUuid(),
                                game.getName(),
                                genreCodes,
                                game.getContext(),
                                game.getCreatedAt());
        }
}
