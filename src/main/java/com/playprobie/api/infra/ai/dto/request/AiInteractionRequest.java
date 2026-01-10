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

	// ===== 질문 정보 (마지막 질문 여부 판단에 사용) =====
	@Schema(description = "설문 ID", example = "123") @JsonProperty("survey_id")
	Long surveyId,

	@Schema(description = "현재 질문 순서", example = "3") @JsonProperty("current_question_order")
	Integer currentQuestionOrder,

	@Schema(description = "전체 질문 수", example = "5") @JsonProperty("total_questions")
	Integer totalQuestions,

	// ===== 꼬리질문 제어 정보 (신규 추가) =====
	@Schema(description = "고정 질문 ID", example = "1") @JsonProperty("fixed_q_id")
	Long fixedQId,

	@Schema(description = "현재 턴 번호 (1=고정질문, 2+=꼬리질문)", example = "1") @JsonProperty("turn_num")
	Integer turnNum,

	@Schema(description = "현재까지 진행된 꼬리질문 횟수", example = "2") @JsonProperty("current_tail_count")
	Integer currentTailCount,

	@Schema(description = "최대 허용 꼬리질문 횟수", example = "3") @JsonProperty("max_tail_questions")
	Integer maxTailQuestions) {

	public static AiInteractionRequest of(
		String sessionId,
		String userAnswer,
		String currentQuestion,
		Map<String, Object> gameInfo,
		List<Map<String, String>> history,
		Long surveyId,
		Integer currentQuestionOrder,
		Integer totalQuestions,
		Long fixedQId,
		Integer turnNum,
		Integer currentTailCount,
		Integer maxTailQuestions) {
		return new AiInteractionRequest(sessionId, userAnswer, currentQuestion, gameInfo, history,
			surveyId, currentQuestionOrder, totalQuestions, fixedQId, turnNum, currentTailCount, maxTailQuestions);
	}
}
