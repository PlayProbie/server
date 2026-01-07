package com.playprobie.api.domain.workspace.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.workspace.domain.Workspace;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "워크스페이스 응답 DTO")
public record WorkspaceResponse(

        @Schema(description = "워크스페이스 UUID", example = "550e8400-e29b-41d4-a716-446655440000") @JsonProperty("workspace_uuid") UUID workspaceUuid,

        @Schema(description = "워크스페이스 이름", example = "My Game Studio") @JsonProperty("name") String name,

        @Schema(description = "프로필 이미지 URL", example = "https://example.com/image.png") @JsonProperty("profile_image_url") String profileImageUrl,

        @Schema(description = "워크스페이스 설명", example = "인디 게임 개발 스튜디오") @JsonProperty("description") String description,

        @Schema(description = "게임 개수", example = "3") @JsonProperty("game_count") Integer gameCount,

        @Schema(description = "생성 일시", example = "2024-01-01T09:00:00+09:00", type = "string") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") @JsonProperty("created_at") OffsetDateTime createdAt,

        @Schema(description = "수정 일시", example = "2024-01-15T14:30:00+09:00", type = "string") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") @JsonProperty("updated_at") OffsetDateTime updatedAt) {

    public static WorkspaceResponse from(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getUuid(),
                workspace.getName(),
                workspace.getProfileImageUrl(),
                workspace.getDescription(),
                workspace.getGames().size(),
                workspace.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                workspace.getUpdatedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
    }

    public static WorkspaceResponse simpleFrom(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getUuid(),
                workspace.getName(),
                workspace.getProfileImageUrl(),
                workspace.getDescription(),
                null,
                workspace.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                workspace.getUpdatedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
    }
}
