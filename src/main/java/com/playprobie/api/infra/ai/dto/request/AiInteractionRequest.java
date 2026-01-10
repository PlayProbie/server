package com.playprobie.api.infra.ai.dto.request;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 상호작용 요청 DTO")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiInteractionRequest(

	@Schema(description = "세션 ID", example = "session_12345") @JsonProperty("session_id")
	String sessionId,

	@Schema(description = "사용자 응답 텍스트", example = "네, 재미있었습니다.") @JsonProperty("user_answer")
	String userAnswer,

	@Schema(description = "현재 질문", example = "게임이 재미있었나요?") @JsonProperty("current_question")
	String currentQuestion,

	@Schema(description = "게임 정보 메타데이터 (key-value)", example = "{\"gameName\": \"RPG Game\"}") @JsonProperty("game_info")
	Map<String, Object> gameInfo,

	@Schema(description = "대화 내역 리스트", example = "[{\"role\": \"user\", \"content\": \"hello\"}]") @JsonProperty("conversation_history")
	List<Map<String, String>> conversationHistory,

	// ===== 질문 정보 추가 (마지막 질문 여부 판단에 사용) =====
	@Schema(description = "설문 ID", example = "123") @JsonProperty("survey_id")
	Long surveyId,

	@Schema(description = "현재 질문 순서", example = "3") @JsonProperty("current_question_order")
	Integer currentQuestionOrder,

	@Schema(description = "전체 질문 수", example = "5") @JsonProperty("total_questions")
	Integer totalQuestions) {

	public static AiInteractionRequest of(
		String sessionId,
		String userAnswer,
		String currentQuestion,
		Map<String, Object> gameInfo,
		List<Map<String, String>> history,
		Long surveyId,
		Integer currentQuestionOrder,
		Integer totalQuestions) {
		return new AiInteractionRequest(sessionId, userAnswer, currentQuestion, gameInfo, history,
			surveyId, currentQuestionOrder, totalQuestions);
	}
}
