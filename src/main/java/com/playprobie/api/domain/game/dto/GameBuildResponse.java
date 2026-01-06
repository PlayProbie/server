package com.playprobie.api.domain.game.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.game.domain.GameBuild;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "게임 빌드 응답 DTO")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameBuildResponse(

        @Schema(description = "빌드 UUID", example = "550e8400-e29b-41d4-a716-446655440000") @JsonProperty("uuid") UUID uuid,

        @Schema(description = "빌드 버전", example = "1.0.0") @JsonProperty("version") String version,

        @Schema(description = "빌드 상태 (PENDING, UPLOADING, UPLOADED, FAILED)", example = "UPLOADED") @JsonProperty("status") String status,

        @Schema(description = "총 파일 수", example = "42") @JsonProperty("total_files") Integer totalFiles,

        @Schema(description = "총 파일 크기 (바이트)", example = "1073741824") @JsonProperty("total_size") Long totalSize,

        @Schema(description = "실행 파일 경로", example = "MyGame/Binaries/Win64/MyGame.exe") @JsonProperty("executable_path") String executablePath,

        @Schema(description = "운영체제 타입 (WINDOWS, LINUX)", example = "WINDOWS") @JsonProperty("os_type") String osType,

        @Schema(description = "생성 일시", example = "2024-01-01T09:00:00+09:00", type = "string") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") @JsonProperty("created_at") OffsetDateTime createdAt) {

    public static GameBuildResponse from(GameBuild gameBuild) {
        return new GameBuildResponse(
                gameBuild.getUuid(),
                gameBuild.getVersion(),
                gameBuild.getStatus().name(),
                gameBuild.getTotalFiles(),
                gameBuild.getTotalSize(),
                gameBuild.getExecutablePath(),
                gameBuild.getOsType(),
                gameBuild.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
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
