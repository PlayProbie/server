package com.playprobie.api.global.util;

import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * HTTP Cookie 관련 유틸리티 클래스
 */
public final class CookieUtils {

    public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

    private CookieUtils() {
        // 인스턴스화 방지
    }

    /**
     * HttpOnly, Secure 속성이 적용된 JWT Access Token 쿠키 생성
     *
     * @param token         JWT 토큰
     * @param maxAgeSeconds 쿠키 만료 시간 (초)
     * @param isSecure      Secure 속성 (HTTPS 환경에서 true)
     * @return ResponseCookie
     */
    public static ResponseCookie createAccessTokenCookie(String token, long maxAgeSeconds, boolean isSecure) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, token)
                .httpOnly(true) // JavaScript에서 접근 불가 (XSS 방지)
                .secure(isSecure) // HTTPS에서만 전송 (운영 환경)
                .path("/") // 모든 경로에서 쿠키 전송
                .maxAge(maxAgeSeconds) // 쿠키 만료 시간
                .sameSite(isSecure ? "Lax" : "Strict") // HTTPS: Same-Site 허용, HTTP: CSRF 방지
                .build();
    }

    /**
     * Access Token 쿠키 삭제 (로그아웃 시 사용)
     *
     * @param isSecure Secure 속성
     * @return ResponseCookie (maxAge=0으로 삭제)
     */
    public static ResponseCookie deleteAccessTokenCookie(boolean isSecure) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(isSecure)
                .path("/")
                .maxAge(0) // 즉시 만료
                .sameSite(isSecure ? "Lax" : "Strict")
                .build();
    }

    /**
     * 요청에서 특정 이름의 쿠키 값 추출
     *
     * @param request    HttpServletRequest
     * @param cookieName 쿠키 이름
     * @return 쿠키 값 (없으면 null)
     */
    public static String getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
