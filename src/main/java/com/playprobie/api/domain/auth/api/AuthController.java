package com.playprobie.api.domain.auth.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.auth.application.AuthService;
import com.playprobie.api.domain.auth.dto.LoginRequest;
import com.playprobie.api.domain.auth.dto.LoginResponse;
import com.playprobie.api.domain.auth.dto.SignupRequest;
import com.playprobie.api.domain.auth.dto.SignupResponse;
import com.playprobie.api.global.common.response.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth API", description = "인증 관련 API (회원가입, 로그인, 로그아웃)")
public class AuthController {

	private final AuthService authService;

	@PostMapping("/signup")
	@Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
	public ResponseEntity<CommonResponse<SignupResponse>> signup(@Valid @RequestBody
	SignupRequest request) {
		SignupResponse response = authService.signup(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.of(response));
	}

	@PostMapping("/login")
	@Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하고 Access Token을 발급합니다.")
	public ResponseEntity<CommonResponse<LoginResponse>> login(@Valid @RequestBody
	LoginRequest request) {
		LoginResponse response = authService.login(request);
		return ResponseEntity.ok(CommonResponse.of(response));
	}

	@PostMapping("/logout")
	@Operation(summary = "로그아웃", description = "클라이언트에서 토큰을 삭제하여 로그아웃합니다.")
	public ResponseEntity<Void> logout() {
		// 클라이언트에서 토큰 삭제 처리 (서버에서는 별도 처리 없음)
		return ResponseEntity.noContent().build();
	}
}
