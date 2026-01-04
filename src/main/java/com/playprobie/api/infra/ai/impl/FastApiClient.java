package com.playprobie.api.infra.ai.impl;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playprobie.api.domain.interview.application.InterviewService;
import com.playprobie.api.domain.interview.dto.UserAnswerRequest;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.infra.ai.AiClient;
import com.playprobie.api.infra.ai.dto.request.AiInteractionRequest;
import com.playprobie.api.infra.ai.dto.request.GenerateFeedbackRequest;
import com.playprobie.api.infra.ai.dto.request.GenerateQuestionRequest;
import com.playprobie.api.infra.ai.dto.response.GenerateFeedbackResponse;
import com.playprobie.api.infra.ai.dto.response.GenerateQuestionResponse;
import com.playprobie.api.infra.sse.dto.QuestionPayload;
import com.playprobie.api.infra.sse.dto.payload.AnalysisPayload;
import com.playprobie.api.infra.sse.dto.payload.ErrorPayload;
import com.playprobie.api.infra.sse.dto.payload.StatusPayload;
import com.playprobie.api.infra.sse.service.SseEmitterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class FastApiClient implements AiClient {

	private final WebClient aiWebClient;
	private final SseEmitterService sseEmitterService;
	private final ObjectMapper objectMapper;
	private final InterviewService interviewService;

	@Override
	public List<String> generateQuestions(String gameName, String gameGenre, String gameContext, String testPurpose) {
		GenerateQuestionRequest request = GenerateQuestionRequest.builder()
			.gameName(gameName)
			.gameGenre(gameGenre)
			.gameContext(gameContext)
			.testPurpose(testPurpose)
			.build();

		Mono<GenerateQuestionResponse> response = aiWebClient.post()
			.uri("/fixed-questions/draft")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON)
			.bodyValue(request)
			.retrieve()
			.bodyToMono(GenerateQuestionResponse.class);

		GenerateQuestionResponse result = response.block();

		return result.getQuestions();
	}

	@Override
	public GenerateFeedbackResponse getQuestionFeedback(GenerateFeedbackRequest request) {
		Mono<GenerateFeedbackResponse> response = aiWebClient.post()
			.uri("/fixed-questions/feedback")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON)
			.bodyValue(request)
			.retrieve()
			.bodyToMono(GenerateFeedbackResponse.class);

		GenerateFeedbackResponse result = response.block();

		return result;
	}

	@Override
	public void streamNextQuestion(String sessionId, UserAnswerRequest userAnswerRequest) {
		Long fixedQId = userAnswerRequest.getFixedQId();
		// 다음 턴 번호 계산 (현재 답변의 턴 + 1)
		int nextTurnNum = userAnswerRequest.getTurnNum() + 1;

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

		final AtomicReference<String> nextAction = new AtomicReference<>(
			null);
		final AtomicBoolean tailQuestionGenerated = new AtomicBoolean(
			false);

		eventStream.subscribe(
			sse -> {
				String data = sse.data();
				parseAndHandleEvent(sessionId, fixedQId, nextTurnNum, data, nextAction, tailQuestionGenerated);
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

	private void parseAndHandleEvent(String sessionId, Long fixedQId, int nextTurnNum, String jsonStr,
		AtomicReference<String> nextAction,
		AtomicBoolean tailQuestionGenerated) {
		try {
			JsonNode rootNode = objectMapper.readTree(jsonStr);
			/**
			 * [NOW] FastAPI Server가 보내는 Data는 "event", "data"가 무조건 있다고 가정
			 */
			String eventType = rootNode.path("event").asText();
			JsonNode dataNode = rootNode.path("data");
			handleEvent(sessionId, fixedQId, nextTurnNum, eventType, dataNode, nextAction, tailQuestionGenerated);
		} catch (JsonProcessingException e) {
			log.error("Failed to parse JSON event. Data: {} | Error: {}", jsonStr, e.getMessage());
		}
	}

	private void handleEvent(String sessionId, Long fixedQId, int nextTurnNum, String eventType, JsonNode dataNode,
		AtomicReference<String> nextAction,
		AtomicBoolean tailQuestionGenerated) {
		switch (eventType) {
			case "start": // 스트리밍 처리 시작
				StatusPayload startPayload = StatusPayload.builder().status(dataNode.path("status").asText()).build();
				sseEmitterService.send(sessionId, "start", startPayload);
				break;

			case "done": // 모든 처리 완료
				// done 이벤트를 클라이언트로 전송
				StatusPayload donePayload = StatusPayload.builder().status("completed").build();
				sseEmitterService.send(sessionId, "done", donePayload);

				String action = nextAction.get();

				// [Robustness] 꼬리질문 action이지만 실제 생성된 내용이 없으면 PASS_TO_NEXT로 변경
				if ("TAIL_QUESTION".equals(action) && !tailQuestionGenerated.get()) {
					log.warn(
						"AI requested TAIL_QUESTION but generated no content. Falling back to PASS_TO_NEXT. sessionId={}",
						sessionId);
					action = "PASS_TO_NEXT";
				}

				if ("PASS_TO_NEXT".equals(action)) {
					// 다음 고정 질문 발송
					FixedQuestionResponse currentQuestion = interviewService.getQuestionById(fixedQId);
					int currentOrder = currentQuestion.qOrder();

					interviewService.getNextQuestion(sessionId, currentOrder)
						.ifPresentOrElse(
							nextQuestion -> sendNextQuestion(sessionId, nextQuestion),
							() -> sendInterviewComplete(sessionId));
				} else {
					// TAIL_QUESTION 이거나 action이 없는 경우 대기
					log.info("Action is {}, waiting for user answer.", action);
				}
				break;

			case "question": // 고정 질문 전송
				Long eventFixedQId = dataNode.path("fixed_q_id").asLong();
				String qType = dataNode.path("q_type").asText();
				String questionText = dataNode.path("question_text").asText();
				int turnNum = dataNode.path("turn_num").asInt();
				QuestionPayload fixedQuestionPayload = QuestionPayload.of(eventFixedQId, qType, questionText, turnNum);
				sseEmitterService.send(sessionId, "question", fixedQuestionPayload);
				break;

			case "analyze_answer": // 답변 분석 완료 -> Action 저장
				String actionResult = dataNode.path("action").asText();
				String analysis = dataNode.path("analysis").asText();

				nextAction.set(actionResult);
				log.info("Analysis result - action: {}, analysis: {}", actionResult, analysis);

				AnalysisPayload analysisPayload = AnalysisPayload.builder().action(actionResult).analysis(analysis)
					.build();
				// sseEmitterService.send(sessionId, eventType, SseResponse.of(sessionId,
				// eventType, analysisPayload));
				break;

			case "token": // 꼬리 질문 생성 중
				tailQuestionGenerated.set(true);
				String content = dataNode.path("content").asText();
				// AI 서버가 주는 turn_num 대신 계산된 nextTurnNum 사용
				QuestionPayload questionPayload = QuestionPayload.of(null, "TAIL", content, nextTurnNum);
				sseEmitterService.send(sessionId, eventType, questionPayload);
				break;

			case "generate_tail_complete": // 꼬리 질문 생성 완료 → DB 저장
				tailQuestionGenerated.set(true);
				String tailQuestionText = dataNode.path("message").asText();
				int tailQuestionCount = dataNode.path("tail_question_count").asInt();
				// 꼬리 질문을 InterviewLog에 저장
				interviewService.saveTailQuestionLog(sessionId, fixedQId, tailQuestionText, tailQuestionCount);
				log.info("Tail question saved - sessionId: {}, fixedQId: {}, count: {}", sessionId, fixedQId,
					tailQuestionCount);
				break;

			case "interview_complete": // 인터뷰 종료
				StatusPayload completePayload = StatusPayload.builder().status("completed").build();
				sseEmitterService.send(sessionId, "interview_complete", completePayload);
				sseEmitterService.complete(sessionId);
				break;

			case "error": // 예외 발생
				String errMessage = dataNode.path("message").asText();
				ErrorPayload errorPayload = ErrorPayload.builder().message(errMessage).build();
				sseEmitterService.send(sessionId, eventType, errorPayload);
				break;

			default:
				log.debug("Unknown event type received: {}", eventType);
		}
	}

	private void sendNextQuestion(String sessionId, FixedQuestionResponse nextQuestion) {
		QuestionPayload questionPayload = QuestionPayload.of(
			nextQuestion.fixedQId(),
			"FIXED",
			nextQuestion.qContent(),
			1);
		sseEmitterService.send(sessionId, "question", questionPayload);
	}

	private void sendInterviewComplete(String sessionId) {
		// 세션 상태 완료로 변경
		interviewService.completeSession(sessionId);

		StatusPayload completePayload = StatusPayload.builder().status("completed").build();
		sseEmitterService.send(sessionId, "interview_complete", completePayload);

		// 클라이언트가 이벤트를 수신할 시간을 확보하기 위해 잠시 대기
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		sseEmitterService.complete(sessionId);
	}
}
