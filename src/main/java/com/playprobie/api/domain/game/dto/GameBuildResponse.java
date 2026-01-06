package com.playprobie.api.domain.game.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.game.domain.GameBuild;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameBuildResponse(
        @JsonProperty("uuid") UUID uuid,
        @JsonProperty("version") String version,
        @JsonProperty("status") String status,
        @JsonProperty("total_files") Integer totalFiles,
        @JsonProperty("total_size") Long totalSize,
        @JsonProperty("executable_path") String executablePath,
        @JsonProperty("os_type") String osType,
        @JsonProperty("created_at") OffsetDateTime createdAt) {

    public static GameBuildResponse from(GameBuild gameBuild) {
        return new GameBuildResponse(
                gameBuild.getUuid(),
                gameBuild.getVersion(),
                gameBuild.getStatus().name(),
                gameBuild.getTotalFiles(),
                gameBuild.getTotalSize(),
                gameBuild.getExecutablePath(),
                gameBuild.getOsType(),
                gameBuild.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }

    public static GameBuildResponse forComplete(GameBuild gameBuild) {
        return new GameBuildResponse(
                gameBuild.getUuid(),
                null,
                gameBuild.getStatus().name(),
                null,
                null,
                gameBuild.getExecutablePath(),
                gameBuild.getOsType(),
                null);
    }
}
