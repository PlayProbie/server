package com.playprobie.api.infra.ai.impl;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playprobie.api.infra.ai.AiClient;
import com.playprobie.api.infra.ai.dto.AiInteractionRequest;
import com.playprobie.api.infra.sse.SseEmitterService;
import com.playprobie.api.infra.sse.dto.QuestionEventData;
import com.playprobie.api.infra.sse.dto.SseResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class FastApiClient implements AiClient {

	private final WebClient aiWebClient;
	private final SseEmitterService sseEmitterService;
	private final ObjectMapper objectMapper;

	@Override
	public java.util.List<String> generateQuestions(String gameName, String gameGenre, String gameContext,
			String testPurpose) {
		throw new UnsupportedOperationException("Unimplemented method 'generateQuestions'");
	}

	@Override
	public java.util.List<String> getQuestionFeedback(String gameName, String gameGenre, String gameContext,
			String testPurpose, String originalQuestion, String feedback) {
		throw new UnsupportedOperationException("Unimplemented method 'getQuestionFeedback'");
	}

	@Override
	public void streamNextQuestion(String sessionId, String userAnswer, String currentQuestion) {
		log.info("Requesting AI stream for sessionId: {}", sessionId);

		AiInteractionRequest aiInteractionRequest = new AiInteractionRequest(
				sessionId,
				userAnswer,
				currentQuestion,
				null,
				null);

		Flux<String> eventStream = aiWebClient.post()
				.uri("/surveys/interaction")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.bodyValue(aiInteractionRequest)
				.exchangeToFlux(response -> {
					return response.bodyToFlux(DataBuffer.class)
							.map(dataBuffer -> {
								byte[] bytes = new byte[dataBuffer.readableByteCount()];
								dataBuffer.read(bytes);
								DataBufferUtils.release(dataBuffer); // 메모리 누수 방지
								return new String(bytes, StandardCharsets.UTF_8);
							});
				});

		eventStream.subscribe(
				chunk -> {
					log.debug("Received chunk: {}", chunk);

					if (chunk == null || chunk.isBlank()) {
						return;
					}

					try {
						String[] lines = chunk.split("\n");
						for (String line : lines) {
							if (line.startsWith("data:")) {
								String jsonStr = line.substring(5).trim();
								if (!jsonStr.isEmpty()) {
									parseAndHandleEvent(sessionId, jsonStr);
								}
							} else {
								String trimmed = line.trim();
								if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
									parseAndHandleEvent(sessionId, trimmed);
								}
							}
						}
					} catch (Exception e) {
						log.error("Streaming parse error: {}", e.getMessage());
					}
				},
				error -> {
					log.error("Error connecting to AI Server: {}", error.getMessage());
					sseEmitterService.send(sessionId, "error", "AI 서버 통신 오류");
					sseEmitterService.complete(sessionId);
				},
				() -> {
					log.info("AI Stream completed for sessionId: {}", sessionId);
				});
	}

	private void parseAndHandleEvent(String sessionId, String jsonStr) {
		try {
			JsonNode rootNode = objectMapper.readTree(jsonStr);

			String eventType = rootNode.path("event").asText();
			JsonNode dataNode = rootNode.path("data");

			handleEvent(sessionId, eventType, dataNode);
		} catch (Exception e) {
			log.warn("Failed to parse JSON event: {} | Content: {}", e.getMessage(), jsonStr);
		}
	}

	private void handleEvent(String sessionId, String eventType, JsonNode dataNode) {
		switch (eventType) {
			case "start":
				sseEmitterService.send(sessionId, "info",
						SseResponse.of("start", "AI 처리를 시작합니다.", dataNode.path("status").asText()));
				break;

			case "analyze_answer":
				sseEmitterService.send(sessionId, "info",
						SseResponse.of("analyze",
								dataNode.path("analysis").asText(),
								dataNode.path("action").asText()));
				break;

			case "token":
				sseEmitterService.send(sessionId, "token",
						SseResponse.of("token",
								dataNode.path("content").asText(),
								null));
				break;

			case "generate_tail_complete":
				String questionText = dataNode.path("message").asText();
				int tailQuestionCount = dataNode.path("tail_question_count").asInt();

				sseEmitterService.send(sessionId, "question",
						QuestionEventData.ofTail(questionText, tailQuestionCount));
				break;

			case "done":
				sseEmitterService.send(sessionId, "done", "세션 종료");
				break;

			case "error":
				sseEmitterService.send(sessionId, "error",
						SseResponse.of("error",
								dataNode.path("message").asText("알 수 없는 오류"),
								null));
				break;

			default:
				log.debug("Unknown event type received: {}", eventType);
		}
	}
}
