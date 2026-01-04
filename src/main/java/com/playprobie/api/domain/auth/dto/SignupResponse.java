package com.playprobie.api.domain.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SignupResponse(
        Long userId,
        String email,
        String name) {
    public static SignupResponse of(Long userId, String email, String name) {
        return new SignupResponse(userId, email, name);
    }
}
