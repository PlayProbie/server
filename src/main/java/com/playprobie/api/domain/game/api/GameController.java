package com.playprobie.api.domain.game.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.game.application.GameService;
import com.playprobie.api.domain.game.dto.CreateGameRequest;
import com.playprobie.api.domain.game.dto.GameResponse;
import com.playprobie.api.domain.game.dto.UpdateGameRequest;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.application.WorkspaceService;
import com.playprobie.api.domain.workspace.domain.Workspace;
import com.playprobie.api.global.common.response.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Game API", description = "게임 관리 API")
public class GameController {

	private final GameService gameService;
	private final WorkspaceService workspaceService;

	@PostMapping("/workspaces/{workspaceUuid}/games")
	@Operation(summary = "게임 생성", description = "특정 워크스페이스 하위에 새 게임을 생성합니다.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "워크스페이스를 찾을 수 없음"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한이 없음")
	})
	public ResponseEntity<CommonResponse<GameResponse>> createGame(
			@AuthenticationPrincipal(expression = "user") User user,
			@Parameter(description = "워크스페이스 UUID") @PathVariable UUID workspaceUuid,
			@Valid @RequestBody CreateGameRequest request) {
		Workspace workspace = workspaceService.getWorkspaceEntity(workspaceUuid);
		GameResponse response = gameService.createGame(workspace, request, user);
		return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.of(response));
	}

	@GetMapping("/workspaces/{workspaceUuid}/games")
	@Operation(summary = "워크스페이스 게임 목록 조회", description = "특정 워크스페이스에 속한 모든 게임 목록을 조회합니다.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "워크스페이스를 찾을 수 없음"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한이 없음")
	})
	public ResponseEntity<CommonResponse<List<GameResponse>>> getGamesByWorkspace(
			@AuthenticationPrincipal(expression = "user") User user,
			@Parameter(description = "워크스페이스 UUID") @PathVariable UUID workspaceUuid) {
		List<GameResponse> responses = gameService.getGamesByWorkspace(workspaceUuid, user);
		return ResponseEntity.ok(CommonResponse.of(responses));
	}

	@GetMapping("/games")
	@Operation(summary = "내 게임 목록 조회", description = "로그인된 사용자가 접근 가능한 모든 게임 목록을 조회합니다.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한이 없음")
	})
	public ResponseEntity<CommonResponse<List<GameResponse>>> getGamesByUser(
			@AuthenticationPrincipal(expression = "user") User user) {
		List<GameResponse> responses = gameService.getGamesByUser(user);
		return ResponseEntity.ok(CommonResponse.of(responses));
	}

	@GetMapping("/games/{gameUuid}")
	@Operation(summary = "게임 상세 조회", description = "게임 상세 정보를 조회합니다.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게임을 찾을 수 없음"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한이 없음")
	})
	public ResponseEntity<CommonResponse<GameResponse>> getGame(
			@AuthenticationPrincipal(expression = "user") User user,
			@Parameter(description = "게임 UUID") @PathVariable UUID gameUuid) {
		GameResponse response = gameService.getGameByUuid(gameUuid, user);
		return ResponseEntity.ok(CommonResponse.of(response));
	}

	@PutMapping("/games/{gameUuid}")
	@Operation(summary = "게임 수정", description = "게임 정보를 수정합니다.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게임을 찾을 수 없음"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한이 없음")
	})
	public ResponseEntity<CommonResponse<GameResponse>> updateGame(
			@AuthenticationPrincipal(expression = "user") User user,
			@Parameter(description = "게임 UUID") @PathVariable UUID gameUuid,
			@Valid @RequestBody UpdateGameRequest request) {
		GameResponse response = gameService.updateGame(gameUuid, request, user);
		return ResponseEntity.ok(CommonResponse.of(response));
	}

	@DeleteMapping("/games/{gameUuid}")
	@Operation(summary = "게임 삭제", description = "게임과 관련된 모든 빌드, 설문을 삭제합니다.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게임을 찾을 수 없음"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한이 없음")
	})
	public ResponseEntity<Void> deleteGame(
			@AuthenticationPrincipal(expression = "user") User user,
			@Parameter(description = "게임 UUID") @PathVariable UUID gameUuid) {
		gameService.deleteGame(gameUuid, user);
		return ResponseEntity.noContent().build();
	}
}
