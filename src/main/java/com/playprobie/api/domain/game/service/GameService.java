package com.playprobie.api.domain.game.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.dto.CreateGameRequest;
import com.playprobie.api.domain.game.dto.GameResponse;
import com.playprobie.api.domain.game.repository.GameRepository;
import com.playprobie.api.global.error.exception.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;

    @Transactional
    public GameResponse createGame(CreateGameRequest request) {
        Game game = Game.builder()
                .name(request.name())
                .genre(request.genre())
                .context(request.context())
                .build();

        Game savedGame = gameRepository.save(game);
        return GameResponse.from(savedGame);
    }

    public GameResponse getGame(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(EntityNotFoundException::new);
        return GameResponse.from(game);
    }

    public Game getGameEntity(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(EntityNotFoundException::new);
    }
}
