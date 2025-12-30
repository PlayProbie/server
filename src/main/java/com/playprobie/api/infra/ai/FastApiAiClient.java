package com.playprobie.api.infra.ai;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FastAPI AI 서버 클라이언트
 * 실제 AI 서버와 HTTP 통신
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class FastApiAiClient implements AiClient {

    private final RestTemplate restTemplate;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Override
    public List<String> generateQuestions(String gameName, String gameGenre, String gameContext, String testPurpose) {
        String url = aiServerUrl + "/fixed-questions/draft";

        Map<String, Object> requestBody = Map.of(
                "game_name", gameName,
                "game_genre", gameGenre,
                "game_context", gameContext,
                "test_purpose", testPurpose);

        HttpEntity<Map<String, Object>> request = createRequest(requestBody);

        try {
            log.debug("Calling AI server: POST {}", url);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response != null && response.containsKey("questions")) {
                @SuppressWarnings("unchecked")
                List<String> questions = (List<String>) response.get("questions");
                log.debug("AI server returned {} questions", questions.size());
                return questions;
            }

            log.warn("AI server returned unexpected response: {}", response);
            return List.of();

        } catch (Exception e) {
            log.error("Failed to call AI server: {}", e.getMessage(), e);
            throw new RuntimeException("AI 서버 호출 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getQuestionFeedback(String gameName, String gameGenre, String gameContext,
            String testPurpose, String originalQuestion, String feedback) {
        String url = aiServerUrl + "/fixed-questions/feedback";

        Map<String, Object> requestBody = Map.of(
                "game_name", gameName,
                "game_genre", gameGenre,
                "game_context", gameContext,
                "test_purpose", testPurpose,
                "original_question", originalQuestion,
                "feedback", feedback);

        HttpEntity<Map<String, Object>> request = createRequest(requestBody);

        try {
            log.debug("Calling AI server: POST {}", url);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response != null && response.containsKey("candidates")) {
                @SuppressWarnings("unchecked")
                List<String> candidates = (List<String>) response.get("candidates");
                log.debug("AI server returned {} candidates", candidates.size());
                return candidates;
            }

            log.warn("AI server returned unexpected response: {}", response);
            return List.of();

        } catch (Exception e) {
            log.error("Failed to call AI server: {}", e.getMessage(), e);
            throw new RuntimeException("AI 서버 호출 실패: " + e.getMessage(), e);
        }
    }

    private HttpEntity<Map<String, Object>> createRequest(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Override
    public void streamNextQuestion(String sessionId, String userAnswer, String currentQuestion) {
        throw new UnsupportedOperationException("Unimplemented method 'streamNextQuestion'");
    }
}
