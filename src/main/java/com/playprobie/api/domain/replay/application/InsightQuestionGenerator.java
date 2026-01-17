package com.playprobie.api.domain.replay.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.playprobie.api.domain.replay.domain.AnalysisTag;
import com.playprobie.api.domain.replay.domain.InsightType;
import com.playprobie.api.domain.replay.dto.InsightQuestionPayload;

import lombok.extern.slf4j.Slf4j;

/**
 * 인사이트 질문 생성기
 * AnalysisTag를 기반으로 하드코딩된 템플릿 질문 생성
 * 랜덤으로 최대 2개 선택
 */
@Slf4j
@Service
public class InsightQuestionGenerator {

	private static final int MAX_INSIGHT_QUESTIONS = 2;

	private static final Map<InsightType, String> TEMPLATES = Map.of(
		InsightType.PANIC,
		"영상의 %d초~%d초 구간에서 버튼을 빠르게 여러 번 누르셨는데, 혹시 당황하셨거나 조작이 어려우셨나요?",
		InsightType.IDLE,
		"영상의 %d초~%d초 구간에서 잠시 멈추셨는데, 어떤 생각을 하고 계셨나요? 혹시 막히는 부분이 있으셨나요?");

	/**
	 * 감지된 태그 중 랜덤으로 최대 2개 선택
	 *
	 * @param allTags 전체 태그 목록
	 * @return 선택된 태그 목록 (최대 2개)
	 */
	public List<AnalysisTag> selectRandomInsights(List<AnalysisTag> allTags) {
		if (allTags == null || allTags.isEmpty()) {
			return List.of();
		}

		if (allTags.size() <= MAX_INSIGHT_QUESTIONS) {
			return new ArrayList<>(allTags);
		}

		// 원본 리스트 보존을 위해 복사 후 셔플
		List<AnalysisTag> shuffled = new ArrayList<>(allTags);
		Collections.shuffle(shuffled);

		log.info("[InsightQuestionGenerator] Selected {} insights from {} total",
			MAX_INSIGHT_QUESTIONS, allTags.size());

		return shuffled.subList(0, MAX_INSIGHT_QUESTIONS);
	}

	/**
	 * AnalysisTag를 기반으로 InsightQuestionPayload 생성
	 *
	 * @param tag       분석 태그
	 * @param turnNum   현재 인사이트 질문 번호 (1부터 시작)
	 * @param remaining 남은 인사이트 질문 수
	 * @return 질문 Payload
	 */
	public InsightQuestionPayload generate(AnalysisTag tag, int turnNum, int remaining) {
		long startMs = tag.getVideoTimeMs();
		int durationMs = tag.getDurationMs() != null ? tag.getDurationMs() : 3000;
		long endMs = startMs + durationMs;

		String template = TEMPLATES.getOrDefault(tag.getInsightType(),
			"영상의 %d초~%d초 구간에서 특이한 행동이 감지되었는데, 그때 어떤 상황이었나요?");

		String questionText = String.format(template,
			startMs / 1000,
			endMs / 1000);

		log.debug("[InsightQuestionGenerator] Generated question for {} at {}ms: {}",
			tag.getInsightType(), startMs, questionText);

		return new InsightQuestionPayload(
			tag.getId(),
			tag.getInsightType(),
			startMs,
			endMs,
			questionText,
			turnNum,
			remaining);
	}
}
