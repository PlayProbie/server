package com.playprobie.api.global.security;

/**
 * Spring Security 인증 Whitelist 상수 정의
 * <p>
 * 인증 없이 접근 가능한 URL 패턴을 관리합니다.
 * 새로운 Public 엔드포인트 추가 시 해당 배열에 패턴을 추가하세요.
 * </p>
 */
public final class SecurityConstants {

	private SecurityConstants() {
		// 인스턴스화 방지
	}

	/**
	 * 인증 없이 접근 가능한 Public URL 패턴 목록
	 * <p>
	 * 이 목록에 포함되지 않은 모든 URL은 인증이 필요합니다.
	 * </p>
	 */
	public static final String[] PUBLIC_URLS = {
			// auth관련
			"/auth/**",

			// api 문서
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/v3/api-docs/**",
			"/api-docs/**",

			// db, health
			"/h2-console/**",
			"/actuator/**",
			"/health",

			// 정적리소스
			"/favicon.ico",
			"/error",

			// 인터뷰 API (비회원 접근 가능)
			"/interview/*",
			"/interview/*/*",
			"/interview/*/stream",
			"/interview/*/messages",

			// 게임/설문 API (개발용 - 추후 인증 적용 필요)
			"/games/**",
			"/surveys/**",
	};
}
