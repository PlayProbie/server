package com.playprobie.api.infra.ai.impl;

import java.util.List;
import java.util.Map;
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
import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.dto.UserAnswerRequest;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.infra.ai.AiClient;
import com.playprobie.api.infra.ai.dto.request.AiInteractionRequest;
import com.playprobie.api.infra.ai.dto.request.AiSessionEndRequest;
import com.playprobie.api.infra.ai.dto.request.AiSessionStartRequest;
import com.playprobie.api.infra.ai.dto.request.GenerateFeedbackRequest;
import com.playprobie.api.infra.ai.dto.request.GenerateQuestionRequest;
import com.playprobie.api.infra.ai.dto.request.QuestionAnalysisRequest;
import com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest;
import com.playprobie.api.infra.ai.dto.response.GenerateFeedbackResponse;
import com.playprobie.api.infra.ai.dto.response.GenerateQuestionResponse;
import com.playprobie.api.infra.ai.dto.response.SessionEmbeddingResponse;
import com.playprobie.api.infra.sse.dto.QuestionPayload;
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

	/**
	 * 고정 질문당 최대 꼬리질문 횟수
	 * 이 횟수를 초과하면 AI 응답과 관계없이 다음 고정 질문으로 진행
	 */
	private static final int MAX_TAIL_QUESTION_COUNT = 2;

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

	/**
	 * AI 서버에 답변 분석 및 꼬리질문 생성 요청을 보냅니다.
	 * SSE 스트리밍으로 응답을 받아 클라이언트에 전달합니다.
	 * 꼬리질문 횟수가 MAX_TAIL_QUESTION_COUNT를 초과하면 AI 호출 없이 다음 질문으로 진행합니다.
	 */
	@Override
	public void streamNextQuestion(String sessionId, UserAnswerRequest userAnswerRequest) {
		// 현재 고정질문 ID 추출
		Long fixedQId = userAnswerRequest.getFixedQId();
		// 다음 턴 번호 계산 (현재 답변의 턴 + 1)
		// 예: 사용자가 turnNum=2에서 답변 → 다음 꼬리질문은 turnNum=3
		int nextTurnNum = userAnswerRequest.getTurnNum() + 1;

		// 현재까지 진행된 꼬리질문 횟수 계산
		// turnNum=1: 고정질문 (꼬리질문 0회)
		// turnNum=2: 첫번째 꼬리질문 응답 (꼬리질문 1회)
		// turnNum=3: 두번째 꼬리질문 응답 (꼬리질문 2회)
		int currentTailCount = userAnswerRequest.getTurnNum() - 1;

		// 디버그 로그: 현재 꼬리질문 횟수와 최대 허용 횟수 출력
		log.info("📊 [TAIL COUNT] sessionId={}, fixedQId={}, currentTailCount={}, max={}",
				sessionId, fixedQId, currentTailCount, MAX_TAIL_QUESTION_COUNT);

		// 꼬리질문 횟수 제한 체크 - 초과 시 AI 호출 없이 바로 다음 질문으로 이동
		if (currentTailCount >= MAX_TAIL_QUESTION_COUNT) {
			log.info("🛑 [TAIL LIMIT EXCEEDED] Skipping AI call, proceeding to next question. sessionId={}", sessionId);
			// AI 호출 없이 바로 다음 고정 질문으로 이동
			handleTailLimitExceeded(sessionId, fixedQId);
			return;
		}

		// AI 서버에 보낼 요청 DTO 생성
		AiInteractionRequest aiInteractionRequest = new AiInteractionRequest(
				sessionId, // 세션 ID
				userAnswerRequest.getAnswerText(), // 사용자 답변
				userAnswerRequest.getQuestionText(), // 현재 질문 텍스트
				null, // game_info (미사용)
				null); // conversation_history (미사용)

		// AI 서버에 SSE 스트리밍 요청 전송
		Flux<ServerSentEvent<String>> eventStream = aiWebClient.post()
				.uri("/surveys/interaction") // AI 서버 엔드포인트
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM) // SSE 응답 타입
				.bodyValue(aiInteractionRequest)
				.retrieve()
				.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
				});

		// AI 응답에서 추출한 action 저장 (TAIL_QUESTION 또는 PASS_TO_NEXT)
		final AtomicReference<String> nextAction = new AtomicReference<>(null);
		// 꼬리질문이 실제로 생성되었는지 여부 추적
		final AtomicBoolean tailQuestionGenerated = new AtomicBoolean(false);

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
				() -> log.info("AI Stream completed for sessionId: {}", sessionId));
	}

	private void parseAndHandleEvent(String sessionId, Long fixedQId, int nextTurnNum, String jsonStr,
			AtomicReference<String> nextAction,
			AtomicBoolean tailQuestionGenerated) {
		log.debug("📥 [SSE RAW] sessionId={}, rawJson={}", sessionId, jsonStr);
		try {
			JsonNode rootNode = objectMapper.readTree(jsonStr);
			/**
			 * [NOW] FastAPI Server가 보내는 Data는 "event", "data"가 무조건 있다고 가정
			 */
			String eventType = rootNode.path("event").asText();
			JsonNode dataNode = rootNode.path("data");
			log.info("📨 [SSE PARSED] sessionId={}, eventType={}, data={}", sessionId, eventType, dataNode);
			handleEvent(sessionId, fixedQId, nextTurnNum, eventType, dataNode, nextAction, tailQuestionGenerated);
		} catch (JsonProcessingException e) {
			log.error("❌ Failed to parse JSON event. Data: {} | Error: {}", jsonStr, e.getMessage());
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
				log.info("✅ [DONE EVENT] sessionId={}, action={}, tailQuestionGenerated={}",
						sessionId, nextAction.get(), tailQuestionGenerated.get());

				// done 이벤트를 클라이언트로 전송
				StatusPayload donePayload = StatusPayload.builder().status("completed").build();
				sseEmitterService.send(sessionId, "done", donePayload);

				String action = nextAction.get();
				log.info("🔍 [ACTION CHECK] sessionId={}, rawAction={}", sessionId, action);

				// [Robustness] 꼬리질문 action이지만 실제 생성된 내용이 없으면 PASS_TO_NEXT로 변경
				if ("TAIL_QUESTION".equals(action) && !tailQuestionGenerated.get()) {
					log.warn(
							"AI requested TAIL_QUESTION but generated no content. Falling back to PASS_TO_NEXT. sessionId={}",
							sessionId);
					action = "PASS_TO_NEXT";
				}

				// AI가 should_end=true를 반환하면 종료 멘트 요청
				boolean shouldEnd = dataNode.path("should_end").asBoolean(false);
				String endReason = dataNode.path("end_reason").asText(null);

				if (shouldEnd) {
					log.info("🛑 [SHOULD_END] AI recommends ending session. reason={}", endReason);
					streamClosing(sessionId, endReason != null ? endReason : "FATIGUE");
					return;
				}

				if ("PASS_TO_NEXT".equals(action)) {
					log.info("➡️ [PASS_TO_NEXT] Proceeding to next question. sessionId={}", sessionId);
					// 다음 고정 질문 발송
					FixedQuestionResponse currentQuestion = interviewService.getQuestionById(fixedQId);
					int currentOrder = currentQuestion.qOrder();

					interviewService.getNextQuestion(sessionId, currentOrder)
							.ifPresentOrElse(
									nextQuestion -> sendNextQuestion(sessionId, nextQuestion),
									() -> streamClosing(sessionId, "ALL_DONE"));
				} else if ("TAIL_QUESTION".equals(action)) {
					// TAIL_QUESTION: 꼬리질문 생성됨, 클라이언트 답변 대기
					log.info("⏳ [TAIL_QUESTION] Waiting for user answer. sessionId={}", sessionId);
				} else {
					// action이 null이거나 알 수 없는 값인 경우
					log.warn("⚠️ [UNKNOWN ACTION] action={}, sessionId={}. Defaulting to wait.", action, sessionId);
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
				break;

			case "token": // 꼬리 질문 생성 중 (레거시 호환)
			case "continue": // 토큰 스트리밍 진행 중 (신규 이벤트)
				tailQuestionGenerated.set(true);
				String content = dataNode.path("content").asText();
				// AI 서버가 주는 turn_num 대신 계산된 nextTurnNum 사용
				QuestionPayload questionPayload = QuestionPayload.of(null, "TAIL", content, nextTurnNum);
				sseEmitterService.send(sessionId, "continue", questionPayload);
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

		// 세션 완료 후 임베딩 요청 (비동기)
		triggerSessionEmbedding(sessionId);

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

	// 세션 완료 시 고정질문별로 그룹핑된 Q&A 데이터를 AI 서버에 임베딩 요청
	private void triggerSessionEmbedding(String sessionId) {
		try {
			Map<Long, List<InterviewLog>> logsByFixedQuestion = interviewService
					.getLogsGroupedByFixedQuestion(sessionId);

			Long surveyId = interviewService.getSurveyIdBySession(sessionId);

			logsByFixedQuestion.forEach((fixedQuestionId, logs) -> {
				List<SessionEmbeddingRequest.QaPair> qaPairs = logs.stream()
						.filter(l -> l.getAnswerText() != null)
						.map(l -> SessionEmbeddingRequest.QaPair.of(
								l.getQuestionText(),
								l.getAnswerText(),
								l.getType().name()))
						.toList();

				if (!qaPairs.isEmpty()) {
					SessionEmbeddingRequest request = SessionEmbeddingRequest.builder()
							.sessionId(sessionId)
							.surveyId(surveyId)
							.fixedQuestionId(fixedQuestionId)
							.qaPairs(qaPairs)
							.autoTriggerAnalysis(true)
							.build();

					// Embedding 요청 후 analysis 자동 트리거
					embedSessionData(request, surveyId, fixedQuestionId).subscribe();
				}
			});
		} catch (Exception e) {
			log.error("Failed to trigger session embedding for sessionId: {}", sessionId, e);
		}
	}

	@Override
	public Mono<SessionEmbeddingResponse> embedSessionData(SessionEmbeddingRequest request) {
		return embedSessionData(request, request.surveyId(), request.fixedQuestionId());
	}

	private Mono<SessionEmbeddingResponse> embedSessionData(SessionEmbeddingRequest request, Long surveyId,
			Long fixedQuestionId) {
		log.debug("📡 Embedding 요청 준비: session={}, fixedQId={}", request.sessionId(), fixedQuestionId);
		return aiWebClient.post()
				.uri("/embeddings")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
						response -> response.bodyToMono(String.class)
								.flatMap(body -> {
									log.error("❌ AI 서버 에러 응답: status={}, body={}", response.statusCode(), body);
									return reactor.core.publisher.Mono.error(
											new RuntimeException(
													"AI Server Error: " + response.statusCode() + " - " + body));
								}))
				.bodyToMono(SessionEmbeddingResponse.class)
				.timeout(java.time.Duration.ofSeconds(30))
				.doOnSubscribe(s -> log.info("📤 Embedding HTTP 요청 전송: session={}, fixedQId={}",
						request.sessionId(), fixedQuestionId))
				.doOnNext(result -> log.info("📥 Embedding 응답 수신: session={}, fixedQId={}",
						request.sessionId(), fixedQuestionId))
				.doOnSuccess(
						result -> {
							log.info("✅ Embedding success for session: {}, fixedQId: {}, embeddingId: {}",
									request.sessionId(), fixedQuestionId, result.embeddingId());
							// Embedding 완료 후 자동으로 analysis 트리거 (플래그 확인)
							if (request.autoTriggerAnalysis() == null || request.autoTriggerAnalysis()) {
								triggerAnalysis(surveyId, fixedQuestionId);
							} else {
								log.info("⏭️ Question {} Auto-trigger analysis skipped", fixedQuestionId);
							}
						})
				.doOnError(
						error -> log.error("❌ Embedding failed for session: {}, fixedQId: {}, error: {}",
								request.sessionId(), fixedQuestionId, error.getMessage(), error));
	}

	@Override
	public void triggerAnalysis(Long surveyId, Long fixedQuestionId) {
		try {
			log.info("🔍 Question {} 분석 시작...", fixedQuestionId);
			// Analysis를 비동기로 시작 (결과는 DB에 저장됨)
			streamQuestionAnalysis(surveyId, fixedQuestionId)
					.subscribe(
							sse -> {
								String event = sse.event();
								String data = sse.data();

								if ("progress".equals(event) && data != null) {
									// JSON 데이터 파싱하여 의미있는 로그 출력
									try {
										JsonNode json = objectMapper.readTree(data);
										String step = json.has("step") ? json.get("step").asText() : "unknown";
										int progress = json.has("progress") ? json.get("progress").asInt() : 0;

										String stepName = getStepName(step);
										log.info("📊 Question {}: {} ({}%)", fixedQuestionId, stepName, progress);
									} catch (Exception e) {
										log.debug("Progress event: {}", data);
									}
								} else if ("error".equals(event)) {
									log.error("❌ Question {} 분석 에러 이벤트: {}", fixedQuestionId, data);
								} else if ("done".equals(event)) {
									log.info("✅ Question {} 분석 완료!", fixedQuestionId);
								} else {
									log.debug("Unknown event for Question {}: {} - {}", fixedQuestionId, event, data);
								}
							},
							error -> log.error("❌ Question {} 분석 실패: {}", fixedQuestionId, error.getMessage()),
							() -> log.info("✅ Question {} 분석 스트림 종료", fixedQuestionId));
		} catch (Exception e) {
			log.error("Failed to trigger analysis for survey: {}, question: {}", surveyId, fixedQuestionId, e);
		}
	}

	private String getStepName(String step) {
		return switch (step) {
			case "loading" -> "로딩 중";
			case "loaded" -> "데이터 로드 완료";
			case "reducing" -> "차원 축소 중";
			case "clustering" -> "클러스터링 중";
			case "extracting_keywords" -> "키워드 추출 중";
			case "analyzing" -> "감정 분석 중";
			case "finalizing" -> "최종 처리 중";
			default -> step;
		};
	}

	@Override
	public Flux<ServerSentEvent<String>> streamQuestionAnalysis(Long surveyId, Long fixedQuestionId) {
		QuestionAnalysisRequest request = QuestionAnalysisRequest.builder()
				.surveyId(surveyId)
				.fixedQuestionId(fixedQuestionId)
				.build();

		return aiWebClient.post()
				.uri("/analytics/questions/{questionId}", fixedQuestionId)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.bodyValue(request)
				.retrieve()
				.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
				});
	}

	/**
	 * 꼬리질문 횟수 제한 초과 시 호출
	 * AI 호출 없이 바로 다음 고정 질문으로 이동
	 */
	private void handleTailLimitExceeded(String sessionId, Long fixedQId) {
		// done 이벤트를 클라이언트로 전송
		StatusPayload donePayload = StatusPayload.builder().status("tail_limit_exceeded").build();
		sseEmitterService.send(sessionId, "done", donePayload);

		// 다음 고정 질문 발송
		FixedQuestionResponse currentQuestion = interviewService.getQuestionById(fixedQId);
		int currentOrder = currentQuestion.qOrder();

		interviewService.getNextQuestion(sessionId, currentOrder)
				.ifPresentOrElse(
						nextQuestion -> sendNextQuestion(sessionId, nextQuestion),
						() -> sendInterviewComplete(sessionId));
	}

	// ========== Session Opening/Closing Methods (Phase 2, 5) ==========

	/**
	 * AI 서버에 세션 시작(오프닝) 요청을 보내고 SSE 스트리밍 응답을 클라이언트로 전달합니다.
	 * Phase 2: 인사말 + 오프닝 질문 생성
	 */
	public void streamOpening(String sessionId, Map<String, Object> gameInfo,
			AiSessionStartRequest.TesterProfileDto testerProfile) {
		AiSessionStartRequest request = AiSessionStartRequest.builder()
				.sessionId(sessionId)
				.gameInfo(gameInfo)
				.testerProfile(testerProfile)
				.build();

		Flux<ServerSentEvent<String>> eventStream = aiWebClient.post()
				.uri("/surveys/start-session")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.bodyValue(request)
				.retrieve()
				.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
				});

		eventStream.subscribe(
				sse -> handleOpeningEvent(sessionId, sse.data()),
				error -> {
					log.error("Error in streamOpening: {}", error.getMessage());
					sseEmitterService.send(sessionId, "error", "오프닝 생성 오류");
				},
				() -> log.info("Opening stream completed for sessionId: {}", sessionId));
	}

	/**
	 * AI 서버에 세션 종료(클로징) 요청을 보내고 SSE 스트리밍 응답을 클라이언트로 전달합니다.
	 * Phase 5: 마무리 멘트 생성 후 인터뷰 완료 처리
	 */
	public void streamClosing(String sessionId, String endReason) {
		AiSessionEndRequest request = AiSessionEndRequest.builder()
				.sessionId(sessionId)
				.endReason(endReason)
				.gameInfo(null)
				.build();

		Flux<ServerSentEvent<String>> eventStream = aiWebClient.post()
				.uri("/surveys/end-session")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.bodyValue(request)
				.retrieve()
				.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
				});

		eventStream.subscribe(
				sse -> handleClosingEvent(sessionId, sse.data()),
				error -> {
					log.error("Error in streamClosing: {}", error.getMessage());
					sendInterviewComplete(sessionId);
				},
				() -> log.info("Closing stream completed for sessionId: {}", sessionId));
	}

	private void handleOpeningEvent(String sessionId, String jsonStr) {
		try {
			JsonNode rootNode = objectMapper.readTree(jsonStr);
			String eventType = rootNode.path("event").asText();
			JsonNode dataNode = rootNode.path("data");

			switch (eventType) {
				case "start":
					StatusPayload startPayload = StatusPayload.builder()
							.status(dataNode.path("status").asText()).build();
					sseEmitterService.send(sessionId, "start", startPayload);
					break;

				case "continue":
					String content = dataNode.path("content").asText();
					QuestionPayload questionPayload = QuestionPayload.of(null, "OPENING", content, 0);
					sseEmitterService.send(sessionId, "continue", questionPayload);
					break;

				case "done":
					String questionText = dataNode.path("question_text").asText();
					QuestionPayload donePayload = QuestionPayload.of(null, "OPENING", questionText, 0);
					sseEmitterService.send(sessionId, "done", donePayload);
					break;

				case "error":
					String errMsg = dataNode.path("message").asText();
					sseEmitterService.send(sessionId, "error", ErrorPayload.builder().message(errMsg).build());
					break;
			}
		} catch (JsonProcessingException e) {
			log.error("Failed to parse opening event: {}", e.getMessage());
		}
	}

	private void handleClosingEvent(String sessionId, String jsonStr) {
		try {
			JsonNode rootNode = objectMapper.readTree(jsonStr);
			String eventType = rootNode.path("event").asText();
			JsonNode dataNode = rootNode.path("data");

			switch (eventType) {
				case "start":
					StatusPayload startPayload = StatusPayload.builder()
							.status(dataNode.path("status").asText()).build();
					sseEmitterService.send(sessionId, "start", startPayload);
					break;

				case "continue":
					String content = dataNode.path("content").asText();
					QuestionPayload questionPayload = QuestionPayload.of(null, "CLOSING", content, 0);
					sseEmitterService.send(sessionId, "continue", questionPayload);
					break;

				case "done":
					// 마무리 멘트 전송 후 인터뷰 완료 처리
					sendInterviewComplete(sessionId);
					break;

				case "error":
					String errMsg = dataNode.path("message").asText();
					sseEmitterService.send(sessionId, "error", ErrorPayload.builder().message(errMsg).build());
					sendInterviewComplete(sessionId);
					break;
			}
		} catch (JsonProcessingException e) {
			log.error("Failed to parse closing event: {}", e.getMessage());
			sendInterviewComplete(sessionId);
		}
	}
}
