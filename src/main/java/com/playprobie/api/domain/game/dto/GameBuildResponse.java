package com.playprobie.api.domain.game.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.playprobie.api.domain.game.domain.GameBuild;

public record GameBuildResponse(
        UUID id,
        UUID gameUuid,
        String version,
        String s3Prefix,
        String status,
        Integer totalFiles,
        Long totalSize,
        String osType,
        String executablePath,
        LocalDateTime createdAt) {
    public static GameBuildResponse from(GameBuild gameBuild) {
        return new GameBuildResponse(
                gameBuild.getUuid(),
                gameBuild.getGame().getUuid(),
                gameBuild.getVersion(),
                gameBuild.getS3Prefix(),
                gameBuild.getStatus().name(),
                gameBuild.getTotalFiles(),
                gameBuild.getTotalSize(),
                gameBuild.getOsType(),
                gameBuild.getExecutablePath(),
                gameBuild.getCreatedAt());
    }
}
