package com.playprobie.api.domain.workspace.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.application.WorkspaceService;
import com.playprobie.api.domain.workspace.dto.InviteMemberRequest;
import com.playprobie.api.domain.workspace.dto.WorkspaceMemberResponse;
import com.playprobie.api.global.common.response.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/workspaces/{workspaceUuid}/members")
@RequiredArgsConstructor
@Tag(name = "Workspace Member API", description = "워크스페이스 멤버 관리 API")
public class WorkspaceMemberController {

	private final WorkspaceService workspaceService;

	@PostMapping
	@Operation(summary = "멤버 초대", description = "이메일로 사용자를 워크스페이스 멤버로 초대합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초대 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "워크스페이스 또는 사용자를 찾을 수 없음"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 멤버임")
	})
	public ResponseEntity<CommonResponse<WorkspaceMemberResponse>> inviteMember(
		@Parameter(description = "워크스페이스 UUID") @PathVariable
		UUID workspaceUuid,
		@Valid @RequestBody
		InviteMemberRequest request,
		@Parameter(hidden = true) @AuthenticationPrincipal(expression = "user")
		User user) {
		WorkspaceMemberResponse response = workspaceService.inviteMember(workspaceUuid, request, user);
		return ResponseEntity.ok(CommonResponse.of(response));
	}

	@GetMapping
	@Operation(summary = "멤버 목록 조회", description = "워크스페이스의 모든 멤버를 조회합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	})
	public ResponseEntity<CommonResponse<List<WorkspaceMemberResponse>>> getMembers(
		@Parameter(description = "워크스페이스 UUID") @PathVariable
		UUID workspaceUuid,
		@Parameter(hidden = true) @AuthenticationPrincipal(expression = "user")
		User user) {
		List<WorkspaceMemberResponse> responses = workspaceService.getMembers(workspaceUuid, user);
		return ResponseEntity.ok(CommonResponse.of(responses));
	}

	@DeleteMapping("/{userId}")
	@Operation(summary = "멤버 내보내기", description = "멤버를 워크스페이스에서 내보냅니다. (Owner만 가능)")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "내보내기 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음")
	})
	public ResponseEntity<Void> removeMember(
		@Parameter(description = "워크스페이스 UUID") @PathVariable
		UUID workspaceUuid,
		@Parameter(description = "내보낼 사용자 ID (PK)") @PathVariable
		Long userId,
		@Parameter(hidden = true) @AuthenticationPrincipal(expression = "user")
		User user) {
		workspaceService.removeMember(workspaceUuid, userId, user);
		return ResponseEntity.noContent().build();
	}
}
