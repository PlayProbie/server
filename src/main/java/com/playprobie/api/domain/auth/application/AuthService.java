package com.playprobie.api.domain.auth.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.auth.dto.LoginRequest;
import com.playprobie.api.domain.auth.dto.LoginResponse;
import com.playprobie.api.domain.auth.dto.SignupRequest;
import com.playprobie.api.domain.auth.dto.SignupResponse;
import com.playprobie.api.domain.auth.exception.EmailDuplicateException;
import com.playprobie.api.domain.auth.exception.InvalidCredentialsException;
import com.playprobie.api.domain.user.dao.UserRepository;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.global.config.properties.JwtProperties;
import com.playprobie.api.global.security.jwt.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final JwtProperties jwtProperties;

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		// 이메일 중복 검사
		if (userRepository.existsByEmail(request.email())) {
			throw new EmailDuplicateException(request.email());
		}

		// 비밀번호 암호화 및 사용자 생성
		String encodedPassword = passwordEncoder.encode(request.password());
		User user = User.createWithEmail(request.email(), encodedPassword, request.name(), request.phone());

		User savedUser = userRepository.save(user);

		return SignupResponse.of(savedUser.getId(), savedUser.getEmail(), savedUser.getName());
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		// 사용자 조회
		User user = userRepository.findByEmail(request.email())
			.orElseThrow(InvalidCredentialsException::new);

		// 비밀번호 검증
		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new InvalidCredentialsException();
		}

		// 계정 상태 확인
		if (!user.getStatus().isActive()) {
			throw new InvalidCredentialsException();
		}

		// JWT 토큰 생성
		String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());

		// DTO 조립 로직 moved from controller
		return LoginResponse.of(accessToken, jwtProperties.accessTokenExpiration(), user);
	}
}
