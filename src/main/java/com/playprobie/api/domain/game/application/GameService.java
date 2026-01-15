package com.playprobie.api.domain.game.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameGenre;
import com.playprobie.api.domain.game.dto.CreateGameRequest;
import com.playprobie.api.domain.game.dto.GameElementExtractRequest;
import com.playprobie.api.domain.game.dto.GameElementExtractResponse;
import com.playprobie.api.domain.game.dto.GameElementExtractResult;
import com.playprobie.api.domain.game.dto.GameResponse;
import com.playprobie.api.domain.game.dto.UpdateGameRequest;
import com.playprobie.api.domain.game.exception.GameNotFoundException;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.application.WorkspaceSecurityManager;
import com.playprobie.api.domain.workspace.application.WorkspaceService;
import com.playprobie.api.domain.workspace.domain.Workspace;
import com.playprobie.api.global.error.exception.InvalidValueException;
import com.playprobie.api.infra.ai.AiClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService {

	private final GameRepository gameRepository;
	private final WorkspaceSecurityManager securityManager;
	private final WorkspaceService workspaceService;
	private final AiClient aiClient;

	public GameElementExtractResult extractElements(GameElementExtractRequest request) {
		GameElementExtractResponse response = aiClient.extractGameElements(request);
		return GameElementExtractResult.from(response);
	}

	@Transactional
	public GameResponse createGame(UUID workspaceUuid, CreateGameRequest request, User user) {
		Workspace workspace = workspaceService.getWorkspaceEntity(workspaceUuid);
		securityManager.validateWriteAccess(workspace, user);

		List<GameGenre> genres = request.gameGenre().stream()
			.map(this::parseGenre)
			.toList();

		Game game = Game.builder()
			.workspace(workspace)
			.name(request.gameName())
			.genres(genres)
			.context(request.gameContext())
			.extractedElements(request.extractedElements())
			.build();

		Game savedGame = gameRepository.save(game);
		return GameResponse.from(savedGame);
	}

	public List<GameResponse> getGamesByWorkspace(UUID workspaceUuid, User user) {
		Workspace workspace = workspaceService.getWorkspaceEntity(workspaceUuid);
		securityManager.validateReadAccess(workspace, user);

		List<Game> games = gameRepository.findByWorkspaceUuid(workspaceUuid);
		return games.stream()
			.map(GameResponse::from)
			.toList();
	}

	public List<GameResponse> getGamesByUser(User user) {
		List<Game> games = gameRepository.findByWorkspace_Members_User_Id(user.getId());
		return games.stream()
			.map(GameResponse::from)
			.toList();
	}

	public GameResponse getGame(Long gameId, User user) {
		Game game = getGameEntity(gameId, user);
		return GameResponse.from(game);
	}

	public Game getGameEntity(Long gameId, User user) {
		Game game = gameRepository.findById(gameId)
			.orElseThrow(GameNotFoundException::new);
		securityManager.validateReadAccess(game.getWorkspace(), user);
		return game;
	}

	public Game getGameEntity(UUID gameUuid, User user) {
		Game game = gameRepository.findByUuid(gameUuid)
			.orElseThrow(GameNotFoundException::new);
		securityManager.validateReadAccess(game.getWorkspace(), user);
		return game;
	}

	public GameResponse getGameByUuid(UUID gameUuid, User user) {
		Game game = getGameEntity(gameUuid, user);
		return GameResponse.from(game);
	}

	@Transactional
	public GameResponse updateGame(UUID gameUuid, UpdateGameRequest request, User user) {
		Game game = gameRepository.findByUuid(gameUuid)
			.orElseThrow(GameNotFoundException::new);
		securityManager.validateWriteAccess(game.getWorkspace(), user);

		List<GameGenre> genres = request.gameGenre().stream()
			.map(this::parseGenre)
			.toList();

		game.update(request.gameName(), genres, request.gameContext(), game.getExtractedElements());
		return GameResponse.from(game);
	}

	@Transactional
	public void deleteGame(UUID gameUuid, User user) {
		Game game = gameRepository.findByUuid(gameUuid)
			.orElseThrow(GameNotFoundException::new);
		securityManager.validateWriteAccess(game.getWorkspace(), user);
		gameRepository.delete(game);
	}

	private GameGenre parseGenre(String code) {
		for (GameGenre g : GameGenre.values()) {
			if (g.name().equalsIgnoreCase(code)) {
				return g;
			}
		}
		throw new InvalidValueException(code);
	}
}
