package com.playprobie.api.domain.game.dto;

import java.time.LocalDateTime;

import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameGenre;

public record GameResponse(
        Long id,
        String name,
        GameGenre genre,
        String context,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static GameResponse from(Game game) {
        return new GameResponse(
                game.getId(),
                game.getName(),
                game.getGenre(),
                game.getContext(),
                game.getCreatedAt(),
                game.getUpdatedAt());
    }
}
