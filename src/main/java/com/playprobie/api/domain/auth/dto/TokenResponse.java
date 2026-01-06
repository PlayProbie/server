package com.playprobie.api.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.playprobie.api.domain.user.domain.User;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TokenResponse(
        String accessToken,
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

    public static TokenResponse of(String accessToken, long expiresInSeconds, User user) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds, UserInfo.from(user));
    }
}
