package com.playprobie.api.domain.interview.domain;

/**
 * 테스터 응답의 품질 평가 결과
 * 유효한 응답(VALID)에만 적용되며, 그 외에는 null
 */
public enum AnswerQuality {
	EMPTY, // 실질적 내용 없음
	GROUNDED, // 기본적인 내용만 있음
	FLOATING, // 내용은 있으나 불충분
	FULL // 충분하고 완전한 응답
}
