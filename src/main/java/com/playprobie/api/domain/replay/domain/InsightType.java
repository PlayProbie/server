package com.playprobie.api.domain.replay.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인사이트 유형 Enum
 * 입력 패턴 분석을 통해 감지되는 특이 행동 유형
 */
@Getter
@RequiredArgsConstructor
public enum InsightType {
	PANIC("특정 시간 내 동일 키 연타"),
	IDLE("10초 이상 입력 부재");

	private final String description;

	/**
	 * 인사이트 질문용 템플릿 프롬프트 반환
	 */
	public String getContextPrompt() {
		return switch (this) {
			case PANIC -> "플레이어가 당황하여 버튼을 연타한 순간이 있었습니다.";
			case IDLE -> "플레이어가 10초 이상 아무 입력 없이 멈춰있던 순간이 있었습니다.";
		};
	}
}
