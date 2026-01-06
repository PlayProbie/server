package com.playprobie.api.domain.auth.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.auth.application.AuthService;
import com.playprobie.api.domain.auth.dto.LoginRequest;
import com.playprobie.api.domain.auth.dto.LoginResponse;
import com.playprobie.api.domain.auth.dto.LoginResult;
import com.playprobie.api.domain.auth.dto.SignupRequest;
import com.playprobie.api.domain.auth.dto.SignupResponse;
import com.playprobie.api.global.common.response.ApiResponse;
import com.playprobie.api.global.util.CookieUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthApi {

    private final AuthService authService;

    /**
     * 운영 환경(HTTPS) 여부 (Secure 쿠키 설정에 사용)
     */
    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request);

        // JWT를 HttpOnly Cookie로 설정
        ResponseCookie accessTokenCookie = CookieUtils.createAccessTokenCookie(
                result.accessToken(),
                result.expiresInSeconds(),
                secureCookie);

        // 응답 body에는 token을 포함하지 않음 (Cookie로 전송)
        LoginResponse response = LoginResponse.of(result.expiresInSeconds(), result.user());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
                .body(ApiResponse.of(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // Access Token 쿠키 삭제
        ResponseCookie deleteCookie = CookieUtils.deleteAccessTokenCookie(secureCookie);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .build();
    }
}
