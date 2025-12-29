package com.playprobie.api.infra.ai;

import java.util.List;

/**
 * AI 클라이언트 인터페이스
 * - 질문 자동 생성
 * - 질문 리뷰 (피드백 + 대안)
 */
public interface AiClient {

    /**
     * 게임 정보와 테스트 목적 기반으로 질문 생성
     *
     * @param gameName    게임 이름
     * @param gameContext 게임 설명
     * @param testPurpose 테스트 목적
     * @param count       생성할 질문 개수
     * @return 생성된 질문 목록
     */
    List<String> generateQuestions(String gameName, String gameContext, String testPurpose, int count);

    /**
     * 질문에 대한 피드백과 대안 제공
     *
     * @param questionContent 질문 내용
     * @return 리뷰 결과 (피드백 + 대안 3개)
     */
    QuestionReview reviewQuestion(String questionContent);

    record QuestionReview(
            String feedback,
            List<String> alternatives) {
    }
}
