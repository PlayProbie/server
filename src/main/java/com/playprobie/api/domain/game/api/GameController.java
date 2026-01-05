package com.playprobie.api.domain.game.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.game.application.GameService;
import com.playprobie.api.domain.game.dto.CreateGameRequest;
import com.playprobie.api.domain.game.dto.GameResponse;
import com.playprobie.api.global.common.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/games")
@RequiredArgsConstructor
public class GameController {

	private final GameService gameService;

	@PostMapping
	public ResponseEntity<ApiResponse<GameResponse>> createGame(@Valid @RequestBody CreateGameRequest request) {
		GameResponse response = gameService.createGame(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
	}

	@GetMapping("/{gameUuid}")
	public ResponseEntity<ApiResponse<GameResponse>> getGame(@PathVariable java.util.UUID gameUuid) {
		GameResponse response = gameService.getGameByUuid(gameUuid);
		return ResponseEntity.ok(ApiResponse.of(response));
	}
}
