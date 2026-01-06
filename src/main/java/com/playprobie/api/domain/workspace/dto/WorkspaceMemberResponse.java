package com.playprobie.api.domain.workspace.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.workspace.domain.WorkspaceMember;
import com.playprobie.api.domain.workspace.domain.WorkspaceRole;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "워크스페이스 멤버 응답")
public record WorkspaceMemberResponse(
        @Schema(description = "멤버 ID (PK)") Long memberId,
        @Schema(description = "사용자 UUID") String userUuid,
        @Schema(description = "사용자 이메일") String email,
        @Schema(description = "사용자 이름") String name,
        @Schema(description = "멤버 권한") WorkspaceRole role,
        @Schema(description = "가입 일시") @JsonProperty("joined_at") LocalDateTime joinedAt) {

    public static WorkspaceMemberResponse from(WorkspaceMember member) {
        return new WorkspaceMemberResponse(
                member.getId(),
                member.getUser().getUuid().toString(),
                member.getUser().getEmail(),
                member.getUser().getName(),
                member.getRole(),
                member.getCreatedAt());
    }
}
