package com.playprobie.api.domain.streaming.domain;

import java.time.ZoneId;

/**
 * Streaming 도메인 상수 정의.
 *
 * <p>
 * 매직 넘버를 제거하고 설정값을 중앙 관리하기 위해 생성.
 */
public final class StreamingConstants {

	private StreamingConstants() {
		// Utility class
	}

	// ========== Session ==========

	/**
	 * 세션 기본 만료 시간 (초).
	 */
	public static final int DEFAULT_SESSION_TIMEOUT_SECONDS = 120;

	// ========== Async Processing ==========

	/**
	 * 비동기 처리 예상 완료 시간 (초).
	 */
	public static final int DEFAULT_ASYNC_COMPLETION_SECONDS = 5;

	// ========== Stream Settings ==========

	/**
	 * 기본 스트림 해상도.
	 */
	public static final String DEFAULT_RESOLUTION = "1080p";

	/**
	 * 기본 스트림 FPS.
	 */
	public static final int DEFAULT_FPS = 60;

	// ========== Timezone ==========

	/**
	 * 기본 타임존 (한국 표준시).
	 * <p>
	 * 향후 글로벌 확장 시 설정 파일로 외부화 가능.
	 */
	public static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("Asia/Seoul");

	/**
	 * UTC 타임존 (권장).
	 */
	public static final ZoneId UTC_TIMEZONE = ZoneId.of("UTC");
}
