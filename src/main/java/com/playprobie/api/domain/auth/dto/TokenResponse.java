package com.playprobie.api.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.playprobie.api.domain.user.domain.User;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 응답 DTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TokenResponse(

        @Schema(description = "JWT 액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") String accessToken,

        @Schema(description = "토큰 타입", example = "Bearer") String tokenType,

        @Schema(description = "토큰 만료 시간 (초)", example = "3600") Long expiresIn,

        @Schema(description = "사용자 정보") UserInfo user) {

    @Schema(description = "사용자 정보")
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserInfo(

            @Schema(description = "사용자 ID", example = "1") Long id,

            @Schema(description = "이메일 주소", example = "user@example.com") String email,

            @Schema(description = "사용자 이름", example = "홍길동") String name,

            @Schema(description = "프로필 이미지 URL", example = "https://example.com/avatar.png") String avatar) {

        public static UserInfo from(User user) {
            return new UserInfo(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    null);
        }
    }

    public static TokenResponse of(String accessToken, long expiresInSeconds, User user) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds, UserInfo.from(user));
    }
}
