package com.playprobie.api.domain.auth.dto;

public record SignupResponse(
        Long userId,
        String email,
        String name) {
    public static SignupResponse of(Long userId, String email, String name) {
        return new SignupResponse(userId, email, name);
    }
}
