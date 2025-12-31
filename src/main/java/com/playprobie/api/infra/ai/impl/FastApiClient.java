package com.playprobie.api.infra.ai.impl;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playprobie.api.domain.interview.dto.UserAnswerRequest;
import com.playprobie.api.infra.ai.AiClient;
import com.playprobie.api.infra.ai.dto.AiInteractionRequest;
import com.playprobie.api.infra.sse.dto.QuestionPayload;
import com.playprobie.api.infra.sse.dto.payload.AnalysisPayload;
import com.playprobie.api.infra.sse.dto.payload.ErrorPayload;
import com.playprobie.api.infra.sse.dto.payload.StatusPayload;
import com.playprobie.api.infra.sse.dto.payload.TailQuestionPayload;
import com.playprobie.api.infra.sse.service.SseEmitterService;
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
	public List<String> generateQuestions(String gameName, String gameGenre, String gameContext, String testPurpose) {
		return List.of();
	}

	@Override
	public List<String> getQuestionFeedback(String gameName, String gameGenre, String gameContext, String testPurpose,
			String originalQuestion, String feedback) {
		return List.of();
	}

	@Override
	public void streamNextQuestion(String sessionId, UserAnswerRequest userAnswerRequest) {

		// TODO: 게임 정보 조회 후 ai-server로 전송
		AiInteractionRequest aiInteractionRequest = new AiInteractionRequest(
				sessionId,
				userAnswerRequest.getAnswerText(),
				userAnswerRequest.getQuestionText(),
				null,
				null);

		Flux<ServerSentEvent<String>> eventStream = aiWebClient.post()
				.uri("/surveys/interaction")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.bodyValue(aiInteractionRequest)
				.retrieve()
				.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
				});

		eventStream.subscribe(
				sse -> {
					String data = sse.data();
					parseAndHandleEvent(sessionId, data);
				},
				error -> {
					log.error("Error connecting to AI Server: {}", error.getMessage());
					sseEmitterService.send(sessionId, "error", "AI 서버 통신 오류");
					sseEmitterService.complete(sessionId);
				},
				() -> {// complete callback function
					log.info("AI Stream completed for sessionId: {}", sessionId);
				});
	}

	private void parseAndHandleEvent(String sessionId, String jsonStr) {
		try {
			JsonNode rootNode = objectMapper.readTree(jsonStr);
			/**
			 * [NOW] FastAPI Server가 보내는 Data는 "event", "data"가 무조건 있다고 가정
			 */
			String eventType = rootNode.path("event").asText();
			JsonNode dataNode = rootNode.path("data");
			handleEvent(sessionId, eventType, dataNode);
		} catch (JsonProcessingException e) {
			log.error("Failed to parse JSON event. Data: {} | Error: {}", jsonStr, e.getMessage());
		}
	}

	private void handleEvent(String sessionId, String eventType, JsonNode dataNode) {
		switch (eventType) {
			case "done": // 모든 처리 완료
			case "start": // 스트리밍 처리 시작
				String status = dataNode.path("status").asText();
				StatusPayload statusPayload = StatusPayload.builder().status(status).build();
				sseEmitterService.send(sessionId, eventType, SseResponse.of(sessionId, eventType, statusPayload));
				break;

			case "analyze_answer": // 답변 분석 완료
				String action = dataNode.path("action").asText();
				String analysis = dataNode.path("analysis").asText();
				AnalysisPayload analysisPayload = AnalysisPayload.builder().action(action).analysis(analysis).build();
				// sseEmitterService.send(sessionId, eventType, SseResponse.of(sessionId,
				// eventType, analysisPayload));
				break;

			case "token": // 꼬리 질문 생성 중
				String content = dataNode.path("content").asText();
				// TODO: question data 가공해서 넣어줘야함.
				QuestionPayload questionPayload = QuestionPayload.of(1L, null, content, 1);
				sseEmitterService.send(
						sessionId,
						eventType,
						SseResponse.of(sessionId + "_" + System.currentTimeMillis(), eventType, questionPayload));
				break;

			case "generate_tail_complete": // 꼬리 질문 생성 완료
				String message = dataNode.path("message").asText();
				int tailQuestionCount = dataNode.path("tail_question_count").asInt();
				TailQuestionPayload tailQuestionPayload = TailQuestionPayload.builder().message(message)
						.tailQuestionCount(tailQuestionCount).build();
				// sseEmitterService.send(sessionId, eventType, SseResponse.of(sessionId,
				// eventType, tailQuestionPayload));
				break;

			case "error": // 예외 발생
				String errMessage = dataNode.path("message").asText();
				ErrorPayload errorPayload = ErrorPayload.builder().message(errMessage).build();
				sseEmitterService.send(sessionId, eventType, SseResponse.of(sessionId, eventType, errorPayload));
				break;

			default:
				log.debug("Unknown event type received: {}", eventType);
		}
	}

}
