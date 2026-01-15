package com.playprobie.api.domain.workspace.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.workspace.domain.WorkspaceMember;
import com.playprobie.api.domain.workspace.domain.WorkspaceRole;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "워크스페이스 멤버 응답 DTO")
public record WorkspaceMemberResponse(

	@Schema(description = "멤버 ID (PK)", example = "1")
	Long memberId,

	@Schema(description = "사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000")
	String userUuid,

	@Schema(description = "사용자 이메일", example = "user@example.com")
	String email,

	@Schema(description = "사용자 이름", example = "홍길동")
	String name,

	@Schema(description = "멤버 권한 (OWNER, MEMBER)", example = "OWNER")
	WorkspaceRole role,

	@Schema(description = "가입 일시", example = "2024-01-01T09:00:00+09:00", type = "string") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul") @JsonProperty("joined_at")
	OffsetDateTime joinedAt) {

	public static WorkspaceMemberResponse from(WorkspaceMember member) {
		return new WorkspaceMemberResponse(
			member.getId(),
			member.getUser().getUuid().toString(),
			member.getUser().getEmail(),
			member.getUser().getName(),
			member.getRole(),
			member.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
	}
}
