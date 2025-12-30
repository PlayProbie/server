package com.playprobie.api.infra.ai;

import java.util.List;

/**
 * AI 클라이언트 인터페이스
 * FastAPI AI 서버와 통신
 */
public interface AiClient {

        /**
         * 고정 질문 초안 생성
         * POST /fixed-questions/draft
         *
         * @param gameName    게임 이름
         * @param gameGenre   게임 장르
         * @param gameContext 게임 설명
         * @param testPurpose 테스트 목적
         * @return 생성된 질문 목록 (최대 5개)
         */
        List<String> generateQuestions(String gameName, String gameGenre, String gameContext, String testPurpose);

        /**
         * 질문 피드백 기반 대안 생성
         * POST /fixed-questions/feedback
         *
         * @param gameName         게임 이름
         * @param gameGenre        게임 장르
         * @param gameContext      게임 설명
         * @param testPurpose      테스트 목적
         * @param originalQuestion 원본 질문
         * @param feedback         사용자 피드백
         * @return 대안 질문 목록 (3개)
         */
        List<String> getQuestionFeedback(String gameName, String gameGenre, String gameContext,
                        String testPurpose, String originalQuestion, String feedback);
}
