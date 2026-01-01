package com.playprobie.api.infra.sse.dto.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

// 4. 질문 생성 완료용 (generate_tail_complete)
// 주의: AI 서버가 주는 필드명(message, count)과 클라이언트가 원하는 필드명이 다를 수 있음
@Getter
@Builder
public class TailQuestionPayload {
	private String message;            // AI가 준 질문 텍스트
	private int tailQuestionCount;   // 꼬리질문 카운트
}
