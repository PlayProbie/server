package com.playprobie.api.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.playprobie.api.domain.user.domain.User;

/**
 * 로그인 응답 DTO (HttpOnly Cookie 방식)
 * <p>
 * access_token은 HttpOnly Cookie로 전송되므로 응답 body에 포함하지 않습니다.
 * </p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LoginResponse(
        String tokenType,
        Long expiresIn,
        UserInfo user) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserInfo(
            Long id,
            String email,
            String name,
            String avatar) {

        public static UserInfo from(User user) {
            return new UserInfo(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    null // avatar == profile img path
            );
        }
    }

    public static LoginResponse of(long expiresInSeconds, User user) {
        return new LoginResponse("Bearer", expiresInSeconds, UserInfo.from(user));
    }
}
