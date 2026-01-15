package com.playprobie.api.domain.survey.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 테스트 단계
 */
@Getter
@RequiredArgsConstructor
public enum TestStage {

	PROTOTYPE("프로토타입 테스트", "prototype"),
	PLAYTEST("첫 외부 플레이테스트", "playtest"),
	PRE_LAUNCH("출시 전 최종 점검", "pre_launch");

	private final String displayName;
	private final String code;
}
