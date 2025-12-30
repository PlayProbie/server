package com.playprobie.api.domain.game.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameGenre;
import com.playprobie.api.domain.game.dto.CreateGameRequest;
import com.playprobie.api.domain.game.dto.GameResponse;
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.global.error.exception.EntityNotFoundException;
import com.playprobie.api.global.error.exception.InvalidValueException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;

    @Transactional
    public GameResponse createGame(CreateGameRequest request) {
        List<GameGenre> genres = request.gameGenre().stream()
                .map(this::parseGenre)
                .toList();

        Game game = Game.builder()
                .name(request.gameName())
                .genres(genres)
                .context(request.gameContext())
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

    private GameGenre parseGenre(String code) {
        for (GameGenre g : GameGenre.values()) {
            if (g.getCode().equals(code)) {
                return g;
            }
        }
        throw new InvalidValueException(code);
    }
}
