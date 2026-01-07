package com.playprobie.api.domain.game.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.game.application.GameBuildService;
import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.game.dto.CompleteUploadRequest;
import com.playprobie.api.domain.game.dto.CreateGameBuildRequest;
import com.playprobie.api.domain.game.dto.CreateGameBuildResponse;
import com.playprobie.api.domain.game.dto.GameBuildResponse;
import com.playprobie.api.global.common.response.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/games/{gameUuid}/builds")
@RequiredArgsConstructor
@Tag(name = "GameBuild", description = "게임 빌드 업로드 API")
public class GameBuildApi {

    private final GameBuildService gameBuildService;

    @GetMapping
    @Operation(summary = "빌드 목록 조회", description = "해당 게임에 속한 모든 빌드 이력을 조회합니다.")
    public ResponseEntity<CommonResponse<java.util.List<GameBuildResponse>>> getBuilds(@PathVariable UUID gameUuid) {
        java.util.List<GameBuildResponse> response = gameBuildService.getBuildsByGameUuid(gameUuid);
        return ResponseEntity.ok(CommonResponse.of(response));
    }

    @PostMapping
    @Operation(summary = "빌드 생성 및 자격 증명 발급", description = "새 게임 빌드를 생성하고, S3 업로드용 임시 자격 증명을 발급합니다.")
    public ResponseEntity<CommonResponse<CreateGameBuildResponse>> createBuild(
            @PathVariable UUID gameUuid,
            @Valid @RequestBody CreateGameBuildRequest request) {
        CreateGameBuildResponse response = gameBuildService.createBuild(gameUuid, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.of(response));
    }

    @PostMapping("/{buildUuid}/complete")
    @Operation(summary = "업로드 완료 처리", description = "S3 업로드 완료를 확인하고 상태를 변경합니다.")
    public ResponseEntity<CommonResponse<GameBuildResponse>> completeUpload(
            @PathVariable UUID gameUuid,
            @PathVariable UUID buildUuid,
            @Valid @RequestBody CompleteUploadRequest request) {
        GameBuild gameBuild = gameBuildService.completeUpload(gameUuid, buildUuid, request);
        return ResponseEntity.ok(CommonResponse.of(GameBuildResponse.forComplete(gameBuild)));
    }

    @DeleteMapping("/{buildUuid}")
    @Operation(summary = "빌드 삭제", description = "빌드와 S3의 모든 파일을 삭제합니다.")
    public ResponseEntity<Void> deleteBuild(
            @PathVariable UUID gameUuid,
            @PathVariable UUID buildUuid) {
        gameBuildService.deleteBuild(gameUuid, buildUuid);
        return ResponseEntity.noContent().build();
    }
}
