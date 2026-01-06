package com.playprobie.api.domain.workspace.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.workspace.application.WorkspaceService;
import com.playprobie.api.domain.workspace.dto.CreateWorkspaceRequest;
import com.playprobie.api.domain.workspace.dto.UpdateWorkspaceRequest;
import com.playprobie.api.domain.workspace.dto.WorkspaceResponse;
import com.playprobie.api.global.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspace API", description = "워크스페이스 관리 API")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    @Operation(summary = "워크스페이스 생성", description = "새로운 워크스페이스를 생성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공")
    })
    public ResponseEntity<ApiResponse<WorkspaceResponse>> createWorkspace(
            @Valid @RequestBody CreateWorkspaceRequest request) {
        // TODO: Get owner from SecurityContext when authentication is implemented
        WorkspaceResponse response = workspaceService.createWorkspace(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping
    @Operation(summary = "워크스페이스 목록 조회", description = "현재 사용자가 소유한 워크스페이스 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> getWorkspaces() {
        // TODO: Get owner from SecurityContext when authentication is implemented
        List<WorkspaceResponse> responses = workspaceService.getWorkspaces(null);
        return ResponseEntity.ok(ApiResponse.of(responses));
    }

    @GetMapping("/{workspaceUuid}")
    @Operation(summary = "워크스페이스 상세 조회", description = "워크스페이스 상세 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "워크스페이스를 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<WorkspaceResponse>> getWorkspace(
            @Parameter(description = "워크스페이스 UUID") @PathVariable UUID workspaceUuid) {
        WorkspaceResponse response = workspaceService.getWorkspace(workspaceUuid);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PutMapping("/{workspaceUuid}")
    @Operation(summary = "워크스페이스 수정", description = "워크스페이스 정보를 수정합니다. Owner만 수정 가능합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "워크스페이스를 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<WorkspaceResponse>> updateWorkspace(
            @Parameter(description = "워크스페이스 UUID") @PathVariable UUID workspaceUuid,
            @Valid @RequestBody UpdateWorkspaceRequest request) {
        // TODO: Verify ownership when authentication is implemented
        WorkspaceResponse response = workspaceService.updateWorkspace(workspaceUuid, request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @DeleteMapping("/{workspaceUuid}")
    @Operation(summary = "워크스페이스 삭제", description = "워크스페이스와 하위 모든 게임을 삭제합니다. Owner만 삭제 가능합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "워크스페이스를 찾을 수 없음")
    })
    public ResponseEntity<Void> deleteWorkspace(
            @Parameter(description = "워크스페이스 UUID") @PathVariable UUID workspaceUuid) {
        // TODO: Verify ownership when authentication is implemented
        workspaceService.deleteWorkspace(workspaceUuid);
        return ResponseEntity.noContent().build();
    }
}
