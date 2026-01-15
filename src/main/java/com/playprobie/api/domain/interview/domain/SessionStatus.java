package com.playprobie.api.domain.interview.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SessionStatus {

	CONNECTED("스트리밍 연결됨"),
	IN_PROGRESS("진행 중"),
	COMPLETED("정상 완료"),
	DROPPED("도중 이탈"),
	TERMINATED("세션 종료됨");

	private final String description;

	/**
	 * 비즈니스 로직: 세션이 완전히 종료되었는지 확인 (성공이든 실패든)
	 * 용도: 더 이상 대화를 진행할 수 없는 상태인지 체크할 때 사용
	 */
	public boolean isFinished() {
		return this == COMPLETED || this == DROPPED || this == TERMINATED;
	}

	/**
	 * 비즈니스 로직: 현재 진행 중인지 확인
	 */
	public boolean isInProgress() {
		return this == IN_PROGRESS;
	}

	/**
	 * 스트리밍 세션이 연결된 상태인지 확인
	 */
	public boolean isConnected() {
		return this == CONNECTED;
	}

	/**
	 * 스트리밍 세션이 종료된 상태인지 확인
	 */
	public boolean isTerminated() {
		return this == TERMINATED;
	}
}
