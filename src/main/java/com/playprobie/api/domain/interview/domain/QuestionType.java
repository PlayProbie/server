package com.playprobie.api.domain.interview.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuestionType {

	FIXED("기획된 고정 질문"),
	TAIL("AI 파생 꼬리 질문"),
	RETRY("재확인 질문");

	private final String description;

	/**
	 * 비즈니스 로직: 꼬리 질문 여부 확인
	 * 서비스 계층에서 if (type == TAIL) 대신 if (type.isTail())을 사용하여 가독성 확보
	 */
	public boolean isTail() {
		return this == TAIL;
	}

	/**
	 * 비즈니스 로직: 고정 질문 여부 확인
	 */
	public boolean isFixed() {
		return this == FIXED;
	}
}
