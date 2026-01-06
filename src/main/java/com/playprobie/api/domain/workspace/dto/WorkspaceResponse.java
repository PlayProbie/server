package com.playprobie.api.domain.workspace.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.workspace.domain.Workspace;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "워크스페이스 응답")
public record WorkspaceResponse(
        @Schema(description = "워크스페이스 UUID", example = "550e8400-e29b-41d4-a716-446655440000") @JsonProperty("workspace_uuid") UUID workspaceUuid,

        @Schema(description = "워크스페이스 이름", example = "My Game Studio") @JsonProperty("name") String name,

        @Schema(description = "프로필 이미지 URL", example = "https://example.com/image.png") @JsonProperty("profile_image_url") String profileImageUrl,

        @Schema(description = "워크스페이스 설명", example = "인디 게임 개발 스튜디오") @JsonProperty("description") String description,

        @Schema(description = "게임 개수", example = "3") @JsonProperty("game_count") Integer gameCount,

        @Schema(description = "생성 일시") @JsonProperty("created_at") LocalDateTime createdAt,

        @Schema(description = "수정 일시") @JsonProperty("updated_at") LocalDateTime updatedAt) {
    public static WorkspaceResponse from(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getUuid(),
                workspace.getName(),
                workspace.getProfileImageUrl(),
                workspace.getDescription(),
                workspace.getGames().size(),
                workspace.getCreatedAt(),
                workspace.getUpdatedAt());
    }

    public static WorkspaceResponse simpleFrom(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getUuid(),
                workspace.getName(),
                workspace.getProfileImageUrl(),
                workspace.getDescription(),
                null,
                workspace.getCreatedAt(),
                workspace.getUpdatedAt());
    }
}
