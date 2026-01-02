package com.playprobie.api.infra.sse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuestionPayload {
	@JsonProperty("fixed_q_id")
	private Long fixedQId; // 고정 질문 ID (없으면 null)

	@JsonProperty("q_type")
	private String qType; // "FIXED" or "TAIL"

	@JsonProperty("question_text")
	private String questionText; // 질문 내용

	@JsonProperty("turn_num")
	private Integer turnNum; // 현재 턴 번호

	// 정적 팩토리 메서드 (생성 편의성)
	public static QuestionPayload of(Long fixedQId, String qType, String questionText, int turnNum) {
		return QuestionPayload.builder()
				.fixedQId(fixedQId)
				.qType(qType)
				.questionText(questionText)
				.turnNum(turnNum)
				.build();
	}
}
