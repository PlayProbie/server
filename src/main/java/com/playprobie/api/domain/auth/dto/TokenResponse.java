package com.playprobie.api.domain.auth.dto;

public record TokenResponse(
        String accessToken,
        String tokenType,
        Long expiresIn) {
    public static TokenResponse of(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
