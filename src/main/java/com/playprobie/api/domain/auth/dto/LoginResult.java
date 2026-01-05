package com.playprobie.api.domain.auth.dto;

import com.playprobie.api.domain.user.domain.User;

/**
 * AuthService.login()의 반환 타입
 * <p>
 * 컨트롤러에서 access_token을 Cookie로 설정하고,
 * 나머지 정보를 응답 body로 반환하기 위해 분리
 * </p>
 */
public record LoginResult(
        String accessToken,
        long expiresInSeconds,
        User user) {

    public static LoginResult of(String accessToken, long expiresInSeconds, User user) {
        return new LoginResult(accessToken, expiresInSeconds, user);
    }
}
