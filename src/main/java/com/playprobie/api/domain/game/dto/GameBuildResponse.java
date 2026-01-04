package com.playprobie.api.domain.game.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.playprobie.api.domain.game.domain.GameBuild;

public record GameBuildResponse(
        UUID id,
        UUID gameUuid,
        String originalFilename,
        String s3Key,
        String status,
        Long fileSize,
        LocalDateTime createdAt) {
    public static GameBuildResponse from(GameBuild gameBuild) {
        return new GameBuildResponse(
                gameBuild.getUuid(),
                gameBuild.getGame().getUuid(),
                gameBuild.getOriginalFilename(),
                gameBuild.getS3Key(),
                gameBuild.getStatus().name(),
                gameBuild.getFileSize(),
                gameBuild.getCreatedAt());
    }
}
