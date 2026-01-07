package com.playprobie.api.domain.survey.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 테마 대분류 (1~3개 선택, 순서가 우선순위)
 */
@Getter
@RequiredArgsConstructor
public enum ThemeCategory {

	GAMEPLAY("게임성 검증", "gameplay"),
	UI_UX("UI/UX 피드백", "ui_ux"),
	BALANCE("밸런스 테스트", "balance"),
	STORY("스토리 평가", "story"),
	BUG("버그 리포트", "bug"),
	OVERALL("종합 평가", "overall");

	private final String displayName;
	private final String code;
}
