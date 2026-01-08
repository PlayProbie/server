package com.playprobie.api.domain.survey.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 설문 상태 Enum.
 *
 * <p>
 * Phase 4-5 워크플로우에서 설문 상태 관리에 사용됩니다.
 */
@Getter
@RequiredArgsConstructor
public enum SurveyStatus {

	DRAFT("초안"),
	ACTIVE("진행 중"),
	CLOSED("종료됨");

	private final String description;

	/**
	 * 설문이 테스터에게 공개된 상태인지 확인합니다.
	 */
	public boolean isOpen() {
		return this == ACTIVE;
	}

	/**
	 * 설문이 종료되어 더 이상 접근할 수 없는 상태인지 확인합니다.
	 */
	public boolean isClosed() {
		return this == CLOSED;
	}
}
