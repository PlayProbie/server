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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth API", description = "인증 관련 API (회원가입, 로그인, 로그아웃)")
public class AuthApi {

    private final AuthService authService;

    /**
     * 운영 환경(HTTPS) 여부 (Secure 쿠키 설정에 사용)
     */
    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    /**
     * 쿠키 도메인 (서브도메인 간 공유 시 사용, 예: .playprobie.shop)
     */
    @Value("${app.cookie.domain:}")
    private String cookieDomain;

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하고 Access Token 쿠키를 발급합니다.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request);

        // JWT를 HttpOnly Cookie로 설정
        ResponseCookie accessTokenCookie = CookieUtils.createAccessTokenCookie(
                result.accessToken(),
                result.expiresInSeconds(),
                secureCookie,
                cookieDomain);

        // 응답 body에는 token을 포함하지 않음 (Cookie로 전송)
        LoginResponse response = LoginResponse.of(result.expiresInSeconds(), result.user());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
                .body(ApiResponse.of(response));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "Access Token 쿠키를 삭제하여 로그아웃합니다.")
    public ResponseEntity<Void> logout() {
        // Access Token 쿠키 삭제
        ResponseCookie deleteCookie = CookieUtils.deleteAccessTokenCookie(secureCookie, cookieDomain);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .build();
    }
}
