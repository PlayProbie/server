package com.playprobie.api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청 DTO")
public record SignupRequest(

        @Schema(description = "이메일 주소", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "이메일은 필수입니다") @Email(message = "올바른 이메일 형식이어야 합니다") String email,

        @Schema(description = "비밀번호 (최소 8자 이상)", example = "securePass123", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "비밀번호는 필수입니다") @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다") String password,

        @Schema(description = "사용자 이름", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "이름은 필수입니다") String name,

        @Schema(description = "전화번호 (선택 사항)", example = "010-1234-5678") String phone) {
}
