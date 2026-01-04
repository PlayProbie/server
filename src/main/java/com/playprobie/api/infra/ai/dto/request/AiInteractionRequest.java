package com.playprobie.api.infra.ai.dto.request;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiInteractionRequest(
	@JsonProperty("session_id") String sessionId,
	@JsonProperty("user_answer") String userAnswer,
	@JsonProperty("current_question") String currentQuestion,

	// "additionalProp1": {} 형태이므로 Map으로 처리
	@JsonProperty("game_info") Map<String, Object> gameInfo,

	// 리스트 내부 요소도 "additionalProp1": "string" 형태이므로 Map의 리스트로 처리
	@JsonProperty("conversation_history") List<Map<String, String>> conversationHistory) {
	// 생성자 팩토리 메서드 (필요시 편의를 위해 추가, 선택사항)
	public static AiInteractionRequest of(
		String sessionId,
		String userAnswer,
		String currentQuestion,
		Map<String, Object> gameInfo,
		List<Map<String, String>> history) {
		return new AiInteractionRequest(sessionId, userAnswer, currentQuestion, gameInfo, history);
	}
}
