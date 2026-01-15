package com.playprobie.api.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 응답 결과 DTO")
public record UserAnswerResponse(

	@Schema(description = "턴 번호", example = "1") @JsonProperty("turn_num")
	Integer turnNum,

	@Schema(description = "질문 유형 (FIXED, AI)", example = "FIXED") @JsonProperty("q_type")
	String qType,

	@Schema(description = "고정 질문 ID", example = "1") @JsonProperty("fixed_q_id")
	Long fixedQId,

	@Schema(description = "질문 텍스트", example = "게임의 조작법은 어떠셨나요?") @JsonProperty("question_text")
	String questionText,

	@Schema(description = "사용자 응답 텍스트", example = "게임 조작법이 직관적이었습니다.") @JsonProperty("answer_text")
	String answerText) {

	public static UserAnswerResponse of(Integer turnNum, String qType, Long fixedQId,
		String questionText, String answerText) {
		return new UserAnswerResponse(turnNum, qType, fixedQId, questionText, answerText);
	}
}
