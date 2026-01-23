package com.playprobie.api.infra.ai;

import java.util.List;
import java.util.Map;

import org.springframework.http.codec.ServerSentEvent;

import com.playprobie.api.domain.game.dto.GameElementExtractRequest;
import com.playprobie.api.domain.game.dto.GameElementExtractResponse;
import com.playprobie.api.domain.interview.dto.UserAnswerRequest;
import com.playprobie.api.infra.ai.dto.request.AiSessionStartRequest.TesterProfileDto;
import com.playprobie.api.infra.ai.dto.request.GenerateFeedbackRequest;
import com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest;
import com.playprobie.api.infra.ai.dto.response.GenerateFeedbackResponse;
import com.playprobie.api.infra.ai.dto.response.SessionEmbeddingResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI 클라이언트 인터페이스
 * FastAPI AI 서버와 통신
 */
public interface AiClient {

	/**
	 * 고정 질문 초안 생성
	 * POST /fixed-questions/draft
	 *
	 * @return 생성된 질문 목록 (최대 5개)
	 */
	List<String> generateQuestions(String gameName, String gameGenre, String gameContext,
		List<String> themePriorities, Map<String, List<String>> themeDetails);

	/**
	 * QuestionBank 기반 질문 추천
	 * POST /api/questions/recommend
	 *
	 * @param request 질문 추천 요청
	 * @return 추천된 질문 목록
	 */
	com.playprobie.api.infra.ai.dto.response.QuestionRecommendResponse recommendQuestions(
		com.playprobie.api.infra.ai.dto.request.QuestionRecommendRequest request);

	/**
	 * 질문 피드백 기반 대안 생성
	 * POST /fixed-questions/feedback
	 *
	 * @return 대안 질문 목록 (3개), feedback
	 */
	GenerateFeedbackResponse getQuestionFeedback(GenerateFeedbackRequest request);

	/**
	 * 게임 요소 추출 요청
	 * POST /game/extract-elements
	 *
	 * @return 추출된 게임 요소 정보
	 */
	GameElementExtractResponse extractGameElements(
		GameElementExtractRequest request);

	/**
	 * streaming 대화 토큰 전달
	 */
	void streamNextQuestion(String sessionId, UserAnswerRequest userAnswerRequest);

	/**
	 * 오프닝 스트리밍 (세션 시작)
	 */
	void streamOpening(String sessionId, Map<String, Object> gameInfo,
		TesterProfileDto testerProfile);

	// 세션 완료 시 임베딩 요청 (비동기)
	Mono<SessionEmbeddingResponse> embedSessionData(SessionEmbeddingRequest request);

	// 질문 분석 수동 트리거 (Mock 데이터 등에서 사용)
	void triggerAnalysis(String surveyUuid, Long fixedQuestionId);

	// 질문 분석 요청 (SSE 스트리밍) - surveyUuid 사용
	Flux<ServerSentEvent<String>> streamQuestionAnalysis(String surveyUuid, Long fixedQuestionId,
		Map<String, String> filters);

	/**
	 * 설문 종합 평가 생성
	 * POST /analytics/survey/summary
	 *
	 * @param questionSummaries 각 질문별 meta_summary 리스트
	 * @return 설문 종합 평가 (1~2문장)
	 */
	Mono<String> generateSurveySummary(List<String> questionSummaries);

	/**
	 * AI 서버 상태 확인
	 * GET /health
	 *
	 * @return true if healthy, false otherwise
	 */
	boolean checkHealth();
}
