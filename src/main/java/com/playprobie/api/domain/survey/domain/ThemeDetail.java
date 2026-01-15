package com.playprobie.api.domain.survey.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 테마 세부항목 (대분류별 소분류)
 */
@Getter
@RequiredArgsConstructor
public enum ThemeDetail {

	// 게임성 검증 (GAMEPLAY)
	CORE_LOOP(ThemeCategory.GAMEPLAY, "코어루프", "core_loop"),
	FUN(ThemeCategory.GAMEPLAY, "재미", "fun"),
	REPLAY_INTENT(ThemeCategory.GAMEPLAY, "재플레이 의향", "replay_intent"),

	// UI/UX 피드백 (UI_UX)
	ONBOARDING(ThemeCategory.UI_UX, "온보딩", "onboarding"),
	CONTROLS(ThemeCategory.UI_UX, "조작감", "controls"),
	CAMERA(ThemeCategory.UI_UX, "카메라", "camera"),
	UI_READABILITY(ThemeCategory.UI_UX, "UI 가독성", "ui_readability"),

	// 밸런스 테스트 (BALANCE)
	DIFFICULTY_CURVE(ThemeCategory.BALANCE, "난이도 곡선", "difficulty_curve"),
	RISK_REWARD(ThemeCategory.BALANCE, "리스크/리워드", "risk_reward"),
	STRATEGY_VARIETY(ThemeCategory.BALANCE, "전략 다양성", "strategy_variety"),
	RESOURCE_BALANCE(ThemeCategory.BALANCE, "자원 밸런스", "resource_balance"),
	FAILURE_STRESS(ThemeCategory.BALANCE, "실패 스트레스", "failure_stress"),

	// 스토리 평가 (STORY)
	COMPREHENSION(ThemeCategory.STORY, "이해도", "comprehension"),
	IMMERSION(ThemeCategory.STORY, "몰입감", "immersion"),
	DIRECTION(ThemeCategory.STORY, "연출", "direction"),
	STORY_GAMEPLAY_INTEGRATION(ThemeCategory.STORY, "스토리-게임플레이 연결", "story_gameplay_integration"),

	// 버그 리포트 (BUG)
	BLOCKER(ThemeCategory.BUG, "블로커", "blocker"),
	MALFUNCTION(ThemeCategory.BUG, "오작동", "malfunction"),
	GRAPHICS_SOUND(ThemeCategory.BUG, "그래픽/사운드", "graphics_sound"),

	// 종합 평가 (OVERALL)
	SATISFACTION(ThemeCategory.OVERALL, "만족도", "satisfaction"),
	RECOMMENDATION(ThemeCategory.OVERALL, "추천 의향", "recommendation"),
	REPLAY(ThemeCategory.OVERALL, "재플레이", "replay"),
	SUMMARY(ThemeCategory.OVERALL, "총평", "summary"),
	IMPROVEMENT(ThemeCategory.OVERALL, "개선점", "improvement");

	private final ThemeCategory category;
	private final String displayName;
	private final String code;
}
