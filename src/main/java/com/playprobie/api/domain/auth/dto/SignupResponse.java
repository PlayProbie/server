package com.playprobie.api.domain.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 응답 DTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SignupResponse(

        @Schema(description = "생성된 사용자 ID", example = "1") Long userId,

        @Schema(description = "이메일 주소", example = "user@example.com") String email,

        @Schema(description = "사용자 이름", example = "홍길동") String name) {

    public static SignupResponse of(Long userId, String email, String name) {
        return new SignupResponse(userId, email, name);
    }
}
