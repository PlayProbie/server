package com.playprobie.api.infra.ai;

import java.util.List;

/**
 * AI 클라이언트 인터페이스
 */
public interface AiClient {

    /**
     * 게임 정보 기반으로 질문 생성
     *
     * @param gameName    게임 이름
     * @param gameGenre   게임 장르 (콤마 구분)
     * @param gameContext 게임 설명
     * @param testPurpose 테스트 목적
     * @param count       생성할 질문 개수
     * @return 생성된 질문 목록
     */
    List<String> generateQuestions(String gameName, String gameGenre, String gameContext, String testPurpose,
            int count);

    /**
     * 질문에 대한 피드백과 대안 제공
     */
    QuestionReview reviewQuestion(String questionContent);

    record QuestionReview(
            String feedback,
            List<String> alternatives) {
    }
}
