package com.playprobie.api.domain.replay.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.replay.domain.AnalysisTag;
import com.playprobie.api.domain.replay.domain.InsightType;
import com.playprobie.api.domain.replay.dto.InputLogDto;

import lombok.extern.slf4j.Slf4j;

/**
 * 입력 로그 분석기 (메모리 분석)
 * 원시 로그를 DB에 저장하지 않고, 요청 즉시 메모리에서 분석 후 AnalysisTag만 생성
 * Stateless 구현으로 Thread-Safe
 */
@Slf4j
@Service
public class InputLogAnalyzer {

	// Panic: 0.5초(500ms) 내 동일 키 5회 이상 연타
	private static final int PANIC_THRESHOLD_MS = 500;
	private static final int PANIC_KEY_COUNT = 5;

	// Idle: 30초(30_000ms) 이후부터 10초(10_000ms) 이상 입력 없으면 감지
	private static final int IDLE_START_THRESHOLD_MS = 30_000;
	private static final int IDLE_THRESHOLD_MS = 10_000;

	// Panic 감지 시 구간 길이 (기본 3초)
	private static final int PANIC_DURATION_MS = 3_000;

	/**
	 * 입력 로그를 메모리에서 분석하여 AnalysisTag 생성
	 * (원시 로그는 저장하지 않음 - Option A)
	 *
	 * @param session 세션 정보
	 * @param logs    media_time 기준 정렬된 로그
	 * @return 감지된 Insight 태그 목록
	 */
	public List<AnalysisTag> analyze(SurveySession session, List<InputLogDto> logs) {
		if (logs == null || logs.isEmpty()) {
			return List.of();
		}

		// media_time 기준 정렬
		List<InputLogDto> sortedLogs = logs.stream()
			.sorted(Comparator.comparingLong(InputLogDto::mediaTime))
			.toList();

		List<AnalysisTag> tags = new ArrayList<>();

		// KEY_DOWN, MOUSE_DOWN 이벤트만 필터링하여 Panic 감지
		List<InputLogDto> inputEvents = sortedLogs.stream()
			.filter(InputLogDto::isInputEvent)
			.toList();

		tags.addAll(detectPanic(session, inputEvents));
		tags.addAll(detectIdle(session, sortedLogs));

		log.info("[InputLogAnalyzer] Session {} - Analyzed {} logs, detected {} insights",
			session.getUuid(), logs.size(), tags.size());

		return tags;
	}

	/**
	 * Panic 감지: 0.5초 내 동일 키 5회 이상 연타 (KEY_DOWN 기준)
	 * Sliding Window 방식으로 구현
	 */
	private List<AnalysisTag> detectPanic(SurveySession session, List<InputLogDto> inputEvents) {
		List<AnalysisTag> panicTags = new ArrayList<>();

		if (inputEvents.size() < PANIC_KEY_COUNT) {
			return panicTags;
		}

		// KEY_DOWN 이벤트만 필터링
		List<InputLogDto> keyDownEvents = inputEvents.stream()
			.filter(log -> "KEY_DOWN".equals(log.type()))
			.toList();

		if (keyDownEvents.size() < PANIC_KEY_COUNT) {
			return panicTags;
		}

		// Sliding Window: 연속된 5개 이벤트가 0.5초 내에 발생하고 동일 키인지 확인
		for (int i = 0; i <= keyDownEvents.size() - PANIC_KEY_COUNT; i++) {
			List<InputLogDto> window = keyDownEvents.subList(i, i + PANIC_KEY_COUNT);

			Long firstTime = window.get(0).mediaTime();
			Long lastTime = window.get(PANIC_KEY_COUNT - 1).mediaTime();
			long duration = lastTime - firstTime;

			// 0.5초 내에 5회 이상 발생했는지 확인
			if (duration <= PANIC_THRESHOLD_MS) {
				// 동일 키인지 확인
				String firstKey = window.get(0).code();
				boolean sameKey = window.stream()
					.allMatch(log -> firstKey != null && firstKey.equals(log.code()));

				if (sameKey) {
					AnalysisTag tag = AnalysisTag.builder()
						.session(session)
						.insightType(InsightType.PANIC)
						.videoTimeMs(firstTime)
						.durationMs(PANIC_DURATION_MS)
						.metadata(String.format("{\"key\":\"%s\",\"count\":%d}", firstKey, PANIC_KEY_COUNT))
						.build();

					panicTags.add(tag);

					// 중복 감지 방지: 다음 윈도우 스킵
					i += PANIC_KEY_COUNT - 1;

					log.debug("[InputLogAnalyzer] Panic detected at {}ms - key: {}", firstTime, firstKey);
				}
			}
		}

		return panicTags;
	}

	/**
	 * Idle 감지: 30초 이후부터 10초 이상 입력 없음 (media_time 간격 기준)
	 */
	private List<AnalysisTag> detectIdle(SurveySession session, List<InputLogDto> allLogs) {
		List<AnalysisTag> idleTags = new ArrayList<>();

		if (allLogs.size() < 2) {
			return idleTags;
		}

		// 입력 이벤트만 필터링 (30초 이후의 이벤트만 대상)
		List<InputLogDto> inputEvents = allLogs.stream()
			.filter(InputLogDto::isInputEvent)
			.filter(log -> log.mediaTime() >= IDLE_START_THRESHOLD_MS)
			.toList();

		if (inputEvents.size() < 2) {
			return idleTags;
		}

		for (int i = 0; i < inputEvents.size() - 1; i++) {
			Long currentTime = inputEvents.get(i).mediaTime();
			Long nextTime = inputEvents.get(i + 1).mediaTime();
			long gap = nextTime - currentTime;

			if (gap >= IDLE_THRESHOLD_MS) {
				AnalysisTag tag = AnalysisTag.builder()
					.session(session)
					.insightType(InsightType.IDLE)
					.videoTimeMs(currentTime)
					.durationMs((int)gap)
					.metadata(String.format("{\"gap_ms\":%d}", gap))
					.build();

				idleTags.add(tag);

				log.debug("[InputLogAnalyzer] Idle detected at {}ms - duration: {}ms", currentTime, gap);
			}
		}

		return idleTags;
	}
}
