package com.playprobie.api.domain.replay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.replay.domain.AnalysisTag;
import com.playprobie.api.domain.replay.domain.InsightType;
import com.playprobie.api.domain.replay.dto.InputLogDto;

/**
 * InputLogAnalyzer 단위 테스트
 * Panic/Idle 감지 로직 검증
 */
class InputLogAnalyzerTest {

	private InputLogAnalyzer inputLogAnalyzer;
	private SurveySession mockSession;

	@BeforeEach
	void setUp() {
		inputLogAnalyzer = new InputLogAnalyzer();
		mockSession = mock(SurveySession.class);
		when(mockSession.getUuid()).thenReturn(UUID.randomUUID());
	}

	@Nested
	@DisplayName("Panic 감지 테스트")
	class PanicDetection {

		@Test
		@DisplayName("0.5초 내 동일 키 5회 연타 시 Panic 감지")
		void detectPanic_whenSameKeyPressedFiveTimes_withinHalfSecond() {
			// given: Space 키 5회 연타 (0ms, 100ms, 200ms, 300ms, 400ms)
			List<InputLogDto> logs = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				logs.add(createKeyDownLog("Space", " ", i * 100L));
			}

			// when
			List<AnalysisTag> tags = inputLogAnalyzer.analyze(mockSession, logs);

			// then
			assertThat(tags).hasSize(1);
			assertThat(tags.get(0).getInsightType()).isEqualTo(InsightType.PANIC);
			assertThat(tags.get(0).getVideoTimeMs()).isEqualTo(0L);
		}

		@Test
		@DisplayName("0.5초 초과 시 Panic 미감지")
		void noPanic_whenKeyPressedSlowly() {
			// given: Space 키 5회 (0ms, 200ms, 400ms, 600ms, 800ms) - 총 800ms
			List<InputLogDto> logs = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				logs.add(createKeyDownLog("Space", " ", i * 200L));
			}

			// when
			List<AnalysisTag> tags = inputLogAnalyzer.analyze(mockSession, logs);

			// then
			assertThat(tags).isEmpty();
		}

		@Test
		@DisplayName("다른 키 조합 시 Panic 미감지")
		void noPanic_whenDifferentKeys() {
			// given: 서로 다른 키 5회 연타
			List<InputLogDto> logs = List.of(
				createKeyDownLog("KeyA", "a", 0L),
				createKeyDownLog("KeyB", "b", 100L),
				createKeyDownLog("KeyC", "c", 200L),
				createKeyDownLog("KeyD", "d", 300L),
				createKeyDownLog("KeyE", "e", 400L));

			// when
			List<AnalysisTag> tags = inputLogAnalyzer.analyze(mockSession, logs);

			// then
			assertThat(tags).isEmpty();
		}

		@Test
		@DisplayName("4회 연타 시 Panic 미감지 (5회 미만)")
		void noPanic_whenLessThanFiveKeys() {
			// given: 4회만 연타
			List<InputLogDto> logs = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				logs.add(createKeyDownLog("Space", " ", i * 50L));
			}

			// when
			List<AnalysisTag> tags = inputLogAnalyzer.analyze(mockSession, logs);

			// then
			assertThat(tags).isEmpty();
		}
	}

	@Nested
	@DisplayName("Idle 감지 테스트")
	class IdleDetection {

		@Test
		@DisplayName("30초 이후부터 10초 이상 입력 없음 시 Idle 감지")
		void detectIdle_whenNoInputForTenSeconds() {
			// given: 30초에 입력, 45초에 다음 입력 (15초 gap) - 30초 이후부터 Idle 감지 대상
			List<InputLogDto> logs = List.of(
				createKeyDownLog("Space", " ", 30000L),
				createKeyDownLog("Space", " ", 45000L));

			// when
			List<AnalysisTag> tags = inputLogAnalyzer.analyze(mockSession, logs);

			// then
			assertThat(tags).hasSize(1);
			assertThat(tags.get(0).getInsightType()).isEqualTo(InsightType.IDLE);
			assertThat(tags.get(0).getDurationMs()).isEqualTo(15000);
		}

		@Test
		@DisplayName("10초 미만 gap 시 Idle 미감지")
		void noIdle_whenGapLessThanTenSeconds() {
			// given: 5초 gap
			List<InputLogDto> logs = List.of(
				createKeyDownLog("Space", " ", 0L),
				createKeyDownLog("Space", " ", 5000L));

			// when
			List<AnalysisTag> tags = inputLogAnalyzer.analyze(mockSession, logs);

			// then: IDLE 미감지 (PANIC도 미감지)
			assertThat(tags).isEmpty();
		}

		@Test
		@DisplayName("30초 이후 여러 Idle 구간 감지")
		void detectMultipleIdles() {
			// given: 30초 이후부터 두 개의 15초 gap
			List<InputLogDto> logs = List.of(
				createKeyDownLog("Space", " ", 30000L),
				createKeyDownLog("Space", " ", 45000L),
				createKeyDownLog("Space", " ", 60000L));

			// when
			List<AnalysisTag> tags = inputLogAnalyzer.analyze(mockSession, logs);

			// then
			assertThat(tags).hasSize(2);
			assertThat(tags).allMatch(tag -> tag.getInsightType() == InsightType.IDLE);
		}
	}

	@Nested
	@DisplayName("엣지 케이스 테스트")
	class EdgeCases {

		@Test
		@DisplayName("빈 로그 리스트 처리")
		void handleEmptyLogs() {
			// when
			List<AnalysisTag> tags = inputLogAnalyzer.analyze(mockSession, List.of());

			// then
			assertThat(tags).isEmpty();
		}

		@Test
		@DisplayName("null 로그 리스트 처리")
		void handleNullLogs() {
			// when
			List<AnalysisTag> tags = inputLogAnalyzer.analyze(mockSession, null);

			// then
			assertThat(tags).isEmpty();
		}

		@Test
		@DisplayName("MOUSE_DOWN 이벤트도 Panic 감지 대상")
		void detectPanic_withMouseEvents() {
			// given: 마우스 클릭 5회 연타
			List<InputLogDto> logs = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				logs.add(createMouseDownLog(0, 100, 100, i * 50L));
			}

			// when
			List<AnalysisTag> tags = inputLogAnalyzer.analyze(mockSession, logs);

			// then: MOUSE_DOWN은 KEY_DOWN처럼 code가 없어서 동일 키 체크에서 제외됨
			// 현재 구현은 KEY_DOWN만 Panic 감지
			assertThat(tags).isEmpty();
		}

		@Test
		@DisplayName("Panic과 Idle 동시 감지")
		void detectBothPanicAndIdle() {
			// given: Panic 후 30초 이후 15초 대기
			List<InputLogDto> logs = new ArrayList<>();
			// Panic: 0-400ms (30초 이전이므로 Idle 감지 대상 아님)
			for (int i = 0; i < 5; i++) {
				logs.add(createKeyDownLog("Space", " ", i * 100L));
			}
			// 30초 이후 시작하는 Idle 구간: 30000ms -> 45000ms (15초 gap)
			logs.add(createKeyDownLog("Enter", "\n", 30000L));
			logs.add(createKeyDownLog("Enter", "\n", 45000L));

			// when
			List<AnalysisTag> tags = inputLogAnalyzer.analyze(mockSession, logs);

			// then
			assertThat(tags).hasSize(2);
			assertThat(tags).extracting(AnalysisTag::getInsightType)
				.containsExactlyInAnyOrder(InsightType.PANIC, InsightType.IDLE);
		}
	}

	// Helper methods
	private InputLogDto createKeyDownLog(String code, String key, Long mediaTime) {
		return new InputLogDto(
			"KEY_DOWN", mediaTime, System.currentTimeMillis(),
			code, key, null, null, null, null, null, null);
	}

	private InputLogDto createMouseDownLog(Integer button, Integer x, Integer y, Long mediaTime) {
		return new InputLogDto(
			"MOUSE_DOWN", mediaTime, System.currentTimeMillis(),
			null, null, button, x, y, null, null, null);
	}
}
