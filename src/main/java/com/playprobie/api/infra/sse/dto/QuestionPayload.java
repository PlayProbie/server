package com.playprobie.api.infra.sse.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class QuestionPayload {
	private Long fixedQuestionId; // 고정 질문 ID (없으면 null)
	private String questionType; // "FIXED" or "TAIL"
	private String questionText; // 질문 내용
	private Integer turnNum; // 현재 턴 번호

	// 정적 팩토리 메서드 (생성 편의성)
	public static QuestionPayload of(Long fixedQuestionId, String questionType, String questionText, int turnNum) {
		return QuestionPayload.builder()
				.fixedQuestionId(fixedQuestionId)
				.questionType(questionType)
				.questionText(questionText)
				.turnNum(turnNum)
				.build();
	}
}
