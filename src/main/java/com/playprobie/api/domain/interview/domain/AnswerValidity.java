package com.playprobie.api.domain.interview.domain;

/**
 * 테스터 응답의 유효성 검사 결과
 */
public enum AnswerValidity {
	VALID, // 유효한 응답
	OFF_TOPIC, // 주제와 무관한 응답
	AMBIGUOUS, // 애매한 응답
	REFUSAL, // 응답 거부
	UNINTELLIGIBLE // 이해 불가능한 응답
}
