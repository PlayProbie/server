package com.playprobie.api.domain.game.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.game.application.GameBuildService;
import com.playprobie.api.domain.game.dto.CompleteUploadRequest;
import com.playprobie.api.domain.game.dto.CreatePresignedUrlRequest;
import com.playprobie.api.domain.game.dto.CreatePresignedUrlResponse;
import com.playprobie.api.domain.game.dto.GameBuildResponse;

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

    @PostMapping("/presigned-url")
    @Operation(summary = "Presigned URL 발급", description = "S3 업로드용 Presigned URL을 발급합니다.")
    public ResponseEntity<CreatePresignedUrlResponse> createPresignedUrl(
            @PathVariable UUID gameUuid,
            @Valid @RequestBody CreatePresignedUrlRequest request) {
        CreatePresignedUrlResponse response = gameBuildService.createPresignedUrl(gameUuid, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{buildId}/complete")
    @Operation(summary = "업로드 완료 처리", description = "S3 업로드 완료를 확인하고 상태를 변경합니다.")
    public ResponseEntity<GameBuildResponse> completeUpload(
            @PathVariable UUID gameUuid,
            @PathVariable UUID buildId,
            @Valid @RequestBody CompleteUploadRequest request) {
        GameBuildResponse response = gameBuildService.completeUpload(gameUuid, buildId, request);
        return ResponseEntity.ok(response);
    }
}
