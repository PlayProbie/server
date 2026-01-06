package com.playprobie.api.domain.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "워크스페이스 멤버 초대 요청")
public record InviteMemberRequest(
        @Schema(description = "초대할 사용자 이메일", example = "friend@example.com") @NotBlank(message = "이메일은 필수입니다.") @Email(message = "올바른 이메일 형식이 아닙니다.") String email) {
}
