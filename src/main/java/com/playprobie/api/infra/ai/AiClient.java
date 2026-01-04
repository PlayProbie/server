package com.playprobie.api.infra.ai;

import java.util.List;

import com.playprobie.api.domain.interview.dto.UserAnswerRequest;
import com.playprobie.api.infra.ai.dto.request.GenerateFeedbackRequest;
import com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest;
import com.playprobie.api.infra.ai.dto.response.GenerateFeedbackResponse;

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
        List<String> generateQuestions(String gameName, String gameGenre, String gameContext, String testPurpose);

        /**
         * 질문 피드백 기반 대안 생성
         * POST /fixed-questions/feedback
         *
         * @return 대안 질문 목록 (3개), feedback
         */
        GenerateFeedbackResponse getQuestionFeedback(GenerateFeedbackRequest request);

        /**
         * streaming 대화 토큰 전달
         */
        void streamNextQuestion(String sessionId, UserAnswerRequest userAnswerRequest);

        // 세션 완료 시 임베딩 요청 (비동기)
        void embedSessionData(SessionEmbeddingRequest request);
}
