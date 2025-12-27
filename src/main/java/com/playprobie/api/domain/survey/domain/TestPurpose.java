package com.playprobie.api.domain.survey.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 설문(테스트) 목적 유형
 */
@Getter
@RequiredArgsConstructor
public enum TestPurpose {

    GAMEPLAY_VALIDATION("게임성 검증", "gameplay-validation"),
    UI_UX_FEEDBACK("UI/UX 피드백", "ui-ux-feedback"),
    BALANCE_TESTING("밸런스 테스트", "balance-testing"),
    STORY_EVALUATION("스토리 평가", "story-evaluation"),
    BUG_REPORTING("버그 리포트", "bug-reporting"),
    OVERALL_EVALUATION("종합 평가", "overall-evaluation");

    private final String displayName;
    private final String code;
}
