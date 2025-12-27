package com.playprobie.api.domain.interview.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SessionStatus {

	IN_PROGRESS("진행 중"),
	COMPLETED("정상 완료"),
	DROPPED("도중 이탈");

	private final String description;

	/**
	 * 비즈니스 로직: 세션이 완전히 종료되었는지 확인 (성공이든 실패든)
	 * 용도: 더 이상 대화를 진행할 수 없는 상태인지 체크할 때 사용
	 */
	public boolean isFinished() {
		return this == COMPLETED || this == DROPPED;
	}

	/**
	 * 비즈니스 로직: 현재 진행 중인지 확인
	 */
	public boolean isInProgress() {
		return this == IN_PROGRESS;
	}
}