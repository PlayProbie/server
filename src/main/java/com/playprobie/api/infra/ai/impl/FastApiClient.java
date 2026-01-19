package com.playprobie.api.infra.ai.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playprobie.api.domain.analytics.event.AnalysisTriggerEvent;
import com.playprobie.api.domain.game.dto.GameElementExtractRequest;
import com.playprobie.api.domain.game.dto.GameElementExtractResponse;
import com.playprobie.api.domain.interview.application.InterviewService;
import com.playprobie.api.domain.interview.domain.AnswerQuality;
import com.playprobie.api.domain.interview.domain.AnswerValidity;
import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.domain.QuestionType;
import com.playprobie.api.domain.interview.dto.UserAnswerRequest;
import com.playprobie.api.domain.replay.application.InsightQuestionService;
import com.playprobie.api.domain.replay.event.InsightPhaseCompleteEvent;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.global.config.properties.AiProperties;
import com.playprobie.api.global.constants.AiConstants;
import com.playprobie.api.infra.ai.AiClient;
import com.playprobie.api.infra.ai.dto.request.AiInteractionRequest;
import com.playprobie.api.infra.ai.dto.request.AiSessionEndRequest;
import com.playprobie.api.infra.ai.dto.request.AiSessionStartRequest;
import com.playprobie.api.infra.ai.dto.request.GenerateFeedbackRequest;
import com.playprobie.api.infra.ai.dto.request.GenerateQuestionRequest;
import com.playprobie.api.infra.ai.dto.request.QuestionAnalysisRequest;
import com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest;
import com.playprobie.api.infra.ai.dto.request.SurveySummaryRequest;
import com.playprobie.api.infra.ai.dto.response.GenerateFeedbackResponse;
import com.playprobie.api.infra.ai.dto.response.GenerateQuestionResponse;
import com.playprobie.api.infra.ai.dto.response.SessionEmbeddingResponse;
import com.playprobie.api.infra.ai.dto.response.SurveySummaryResponse;
import com.playprobie.api.infra.sse.dto.QuestionPayload;
import com.playprobie.api.infra.sse.dto.payload.ErrorPayload;
import com.playprobie.api.infra.sse.dto.payload.ReactionPayload;
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
	private final AiProperties aiProperties;
	private final com.playprobie.api.domain.survey.dao.SurveyRepository surveyRepository;
	private final com.playprobie.api.domain.interview.dao.SurveySessionRepository surveySessionRepository;
	private final org.springframework.context.ApplicationEventPublisher eventPublisher;
	private final InsightQuestionService insightQuestionService;

	@Override
	public com.playprobie.api.infra.ai.dto.response.QuestionRecommendResponse recommendQuestions(
		com.playprobie.api.infra.ai.dto.request.QuestionRecommendRequest request) {

		log.info("📤 AI 질문 추천 요청: gameName={}, categories={}", request.gameName(), request.purposeCategories());

		return aiWebClient.post()
			.uri("/questions/recommend")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON)
			.bodyValue(request)
			.retrieve()
			.onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
				clientResponse -> clientResponse.bodyToMono(String.class)
					.flatMap(body -> {
						log.error("❌ AI 서버 에러: status={}, body={}", clientResponse.statusCode(), body);
						return reactor.core.publisher.Mono.error(
							new RuntimeException("AI Server Error: " + clientResponse.statusCode()
								+ " - " + body));
					}))
			.bodyToMono(com.playprobie.api.infra.ai.dto.response.QuestionRecommendResponse.class)
			.doOnNext(response -> log.info("📥 AI 질문 추천 응답: {} 개 질문 수신. 내용={}",
				response.questions() != null ? response.questions().size() : 0,
				response.questions()))
			.timeout(java.time.Duration.ofSeconds(30))
			.block();
	}

	@Override
	public List<String> generateQuestions(String gameName, String gameGenre, String gameContext,
		List<String> themePriorities, Map<String, List<String>> themeDetails) {
		GenerateQuestionRequest request = GenerateQuestionRequest.builder()
			.gameName(gameName)
			.gameGenre(gameGenre)
			.gameContext(gameContext)
			.themePriorities(themePriorities)
			.themeDetails(themeDetails)
			.build();

		log.info("📤 AI 질문 생성 요청 (Legacy): gameName={}, gameGenre={}, themePriorities={}", gameName, gameGenre,
			themePriorities);

		try {
			GenerateQuestionResponse result = aiWebClient.post()
				.uri("/fixed-questions/draft")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
					clientResponse -> clientResponse.bodyToMono(String.class)
						.flatMap(body -> {
							log.error("❌ AI 서버 에러: status={}, body={}", clientResponse.statusCode(), body);
							return reactor.core.publisher.Mono.error(
								new RuntimeException("AI Server Error: " + clientResponse.statusCode()
									+ " - " + body));
						}))
				.bodyToMono(GenerateQuestionResponse.class)
				.block();

			if (result == null || result.getQuestions() == null) {
				log.error("❌ AI 서버 응답이 null입니다");
				throw new RuntimeException("AI 서버로부터 응답을 받지 못했습니다");
			}

			log.info("📥 AI 질문 생성 완료: {} 개의 질문 생성", result.getQuestions().size());
			return result.getQuestions();
		} catch (Exception e) {
			log.error("❌ AI 질문 생성 실패: {}", e.getMessage(), e);
			throw e;
		}
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
	public GameElementExtractResponse extractGameElements(
		GameElementExtractRequest request) {

		return aiWebClient.post()
			.uri("/game/extract-elements")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON)
			.bodyValue(request)
			.retrieve()
			.onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
				clientResponse -> clientResponse.bodyToMono(String.class)
					.flatMap(body -> {
						log.error("❌ AI 서버 에러: status={}, body={}", clientResponse.statusCode(), body);
						return reactor.core.publisher.Mono.error(
							new RuntimeException("AI Server Error: " + clientResponse.statusCode()
								+ " - " + body));
					}))
			.bodyToMono(GameElementExtractResponse.class)
			.timeout(java.time.Duration.ofSeconds(60))
			.block();
	}

	/**
	 * AI 서버에 답변 분석 및 꼬리질문 생성 요청을 보냅니다.
	 * SSE 스트리밍으로 응답을 받아 클라이언트에 전달합니다.
	 * 꼬리질문 횟수가 MAX_TAIL_QUESTION_COUNT를 초과하면 AI 호출 없이 다음 질문으로 진행합니다.
	 */
	// Custom Exception for stopping the stream
	private static class SseConnectionClosedException extends RuntimeException {
		public SseConnectionClosedException(String message) {
			super(message);
		}
	}

	@Override
	public void streamNextQuestion(String sessionId, UserAnswerRequest userAnswerRequest) {
		// ... (omitted previous logic for brevity, keeping it identical until eventStream creation)
		Long fixedQuestionId = userAnswerRequest.getFixedQId();
		int nextTurnNum = userAnswerRequest.getTurnNum() + 1;
		int currentTailCount = userAnswerRequest.getTurnNum() - 1;

		int maxTailQuestions = aiProperties.interview().maxTailQuestions();
		log.info("📊 [TAIL COUNT] sessionId={}, fixedQuestionId={}, currentTailCount={}, max={}",
			sessionId, fixedQuestionId, currentTailCount, maxTailQuestions);

		if (currentTailCount >= maxTailQuestions) {
			log.info("🛑 [TAIL LIMIT EXCEEDED] Skipping AI call, proceeding to next question. sessionId={}", sessionId);
			handleTailLimitExceeded(sessionId, fixedQuestionId);
			return;
		}

		FixedQuestionResponse currentQuestion = interviewService.getQuestionById(fixedQuestionId);
		int currentQuestionOrder = currentQuestion.qOrder();

		Long surveyId = interviewService.getSurveyIdBySession(sessionId);
		int totalQuestions = interviewService.getTotalQuestionCount(surveyId);

		log.info("📋 [QUESTION INFO] sessionId={}, surveyId={}, currentOrder={}, totalQuestions={}",
			sessionId, surveyId, currentQuestionOrder, totalQuestions);

		AiInteractionRequest aiInteractionRequest = AiInteractionRequest.of(
			sessionId,
			userAnswerRequest.getAnswerText(),
			currentQuestion.qContent(),
			null,
			interviewService.getConversationHistory(sessionId, fixedQuestionId),
			surveyId,
			currentQuestionOrder,
			totalQuestions,
			fixedQuestionId,
			userAnswerRequest.getTurnNum(),
			currentTailCount,
			maxTailQuestions,
			interviewService.getRetryCount(sessionId, fixedQuestionId));

		Flux<ServerSentEvent<String>> eventStream = aiWebClient.post()
			.uri("/surveys/interaction")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.TEXT_EVENT_STREAM)
			.bodyValue(aiInteractionRequest)
			.retrieve()
			.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});

		final AtomicReference<String> nextAction = new AtomicReference<>(null);
		final AtomicBoolean tailQuestionGenerated = new AtomicBoolean(false);
		final AtomicReference<AnswerValidity> validityRef = new AtomicReference<>(null);
		final AtomicReference<AnswerQuality> qualityRef = new AtomicReference<>(null);

		eventStream
			.doOnNext(sse -> {
				// [FIX] Check connection status before processing
				// We call send with a dummy event or check status if possible.
				// Since we can't easily check status without sending, we rely on the implementation inside parseAndHandleEvent
				// But parseAndHandleEvent calls sseEmitterService.send(). We need to update that method to throw exception.
			})
			.subscribe(
				sse -> {
					String data = sse.data();
					// [FIX] If parseAndHandleEvent returns false (send failed), throwing exception is handled inside.
					// Let's modify parseAndHandleEvent to return boolean or throw exception.
					// Ideally, we handle it here.
					boolean success = parseAndHandleEvent(sessionId, fixedQuestionId, nextTurnNum, data, nextAction,
						tailQuestionGenerated,
						currentQuestionOrder, totalQuestions, validityRef, qualityRef, currentTailCount,
						maxTailQuestions);

					if (!success) {
						throw new SseConnectionClosedException("Client disconnected");
					}
				},
				error -> {
					if (error instanceof SseConnectionClosedException) {
						log.warn("👋 [STREAM STOPPED] Client disconnected during AI stream. sessionId={}", sessionId);
						// Do NOT call complete(sessionId) or send error, as client is gone.
					} else {
						log.error("Error connecting to AI Server: {}", error.getMessage());
						// Try to notify client, but if it fails, it fails.
						sseEmitterService.send(sessionId, AiConstants.EVENT_ERROR, "AI 서버 통신 오류");
						sseEmitterService.complete(sessionId);
					}
				},
				() -> log.info("AI Stream completed for sessionId: {}", sessionId));
	}

	private boolean parseAndHandleEvent(String sessionId, Long fixedQuestionId, int nextTurnNum, String jsonStr,
		AtomicReference<String> nextAction,
		AtomicBoolean tailQuestionGenerated, Integer order, Integer totalQuestions,
		AtomicReference<AnswerValidity> validityRef, AtomicReference<AnswerQuality> qualityRef, int currentTailCount,
		int maxTailQuestions) {
		log.debug("📥 [SSE RAW] sessionId={}, rawJson={}", sessionId, jsonStr);
		try {
			JsonNode rootNode = objectMapper.readTree(jsonStr);
			String eventType = rootNode.path("event").asText();
			JsonNode dataNode = rootNode.path("data");
			log.info("📨 [SSE PARSED] sessionId={}, eventType={}, data={}", sessionId, eventType, dataNode);
			return handleEvent(sessionId, fixedQuestionId, nextTurnNum, eventType, dataNode, nextAction,
				tailQuestionGenerated,
				order,
				totalQuestions, validityRef, qualityRef, currentTailCount, maxTailQuestions);
		} catch (JsonProcessingException e) {
			log.error("❌ Failed to parse JSON event. Data: {} | Error: {}", jsonStr, e.getMessage());
			return true; // JSON parsing error shouldn't stop stream, technically connection is fine
		}
	}

	private boolean handleEvent(String sessionId, Long fixedQuestionId, int nextTurnNum, String eventType,
		JsonNode dataNode,
		AtomicReference<String> nextAction,
		AtomicBoolean tailQuestionGenerated, Integer order, Integer totalQuestions,
		AtomicReference<AnswerValidity> validityRef, AtomicReference<AnswerQuality> qualityRef, int currentTailCount,
		int maxTailQuestions) {
		switch (eventType) {
			case AiConstants.EVENT_START:
				StatusPayload startPayload = StatusPayload.builder().status(dataNode.path("status").asText()).build();
				return sseEmitterService.send(sessionId, AiConstants.EVENT_START, startPayload);

			case AiConstants.EVENT_VALIDITY_RESULT:
				String validityStr = dataNode.path("validity").asText();
				double confidence = dataNode.path("confidence").asDouble();
				String reason = dataNode.path("reason").asText();
				String source = dataNode.path("source").asText();
				log.info("📋 [VALIDITY] sessionId={}, validity={}, confidence={}, reason={}, source={}",
					sessionId, validityStr, confidence, reason, source);
				try {
					validityRef.set(AnswerValidity.valueOf(validityStr));
				} catch (IllegalArgumentException e) {
					log.warn("Unknown validity value: {}", validityStr);
				}
				return true;

			case AiConstants.EVENT_QUALITY_RESULT:
				String qualityStr = dataNode.path("quality").asText();
				String thickness = dataNode.path("thickness").asText();
				String richness = dataNode.path("richness").asText();
				log.info("📊 [QUALITY] sessionId={}, quality={}, thickness={}, richness={}",
					sessionId, qualityStr, thickness, richness);
				try {
					qualityRef.set(AnswerQuality.valueOf(qualityStr));
				} catch (IllegalArgumentException e) {
					log.warn("Unknown quality value: {}", qualityStr);
				}
				return true;

			case AiConstants.EVENT_DONE:
				log.info("✅ [DONE EVENT] sessionId={}, action={}, tailQuestionGenerated={}",
					sessionId, nextAction.get(), tailQuestionGenerated.get());

				if (validityRef.get() != null) {
					int answerTurnNum = nextTurnNum - 1;
					interviewService.updateLogValidityQuality(sessionId, fixedQuestionId, answerTurnNum,
						validityRef.get(), qualityRef.get());
				}

				StatusPayload donePayload = StatusPayload.builder().status("completed").build();
				if (!sseEmitterService.send(sessionId, AiConstants.EVENT_DONE, donePayload))
					return false;

				String action = nextAction.get();
				log.info("🔍 [ACTION CHECK] sessionId={}, rawAction={}", sessionId, action);

				if ("LIMIT_EXCEEDED_HANDLED".equals(action)) {
					log.info(
						"🛑 [LIMIT HANDLED] Already handled RETRY suppression. Skipping DONE event logic. sessionId={}",
						sessionId);
					return true;
				}

				if (AiConstants.ACTION_TAIL_QUESTION.equals(action) && !tailQuestionGenerated.get()) {
					log.warn(
						"AI requested TAIL_QUESTION but generated no content. Falling back to PASS_TO_NEXT. sessionId={}",
						sessionId);
					action = AiConstants.ACTION_PASS_TO_NEXT;
				}
				boolean shouldEnd = dataNode.path("should_end").asBoolean(false);
				String endReason = dataNode.path("end_reason").asText(null);

				if (shouldEnd) {
					log.info("🛑 [SHOULD_END] AI recommends ending session. reason={}", endReason);
					streamClosing(sessionId, endReason != null ? endReason : AiConstants.REASON_FATIGUE);
					return true;
				}

				if (AiConstants.ACTION_PASS_TO_NEXT.equals(action)) {
					log.info("➡️ [PASS_TO_NEXT] Proceeding to next question. sessionId={}", sessionId);
					FixedQuestionResponse currentQuestion = interviewService.getQuestionById(fixedQuestionId);
					int currentOrder = currentQuestion.qOrder();

					interviewService.getNextQuestion(sessionId, currentOrder)
						.ifPresentOrElse(
							nextQuestion -> sendNextQuestion(sessionId, nextQuestion),
							() -> proceedToClosingOrInsight(sessionId, AiConstants.REASON_ALL_DONE));
				} else if (AiConstants.ACTION_TAIL_QUESTION.equals(action)) {
					log.info("⏳ [TAIL_QUESTION] Waiting for user answer. sessionId={}", sessionId);
				} else if (AiConstants.ACTION_RETRY_QUESTION.equals(action)) {
					log.info("🔄 [RETRY_QUESTION] Waiting for user retry. sessionId={}", sessionId);
				} else {
					log.warn("⚠️ [UNKNOWN ACTION] action={}, sessionId={}. Defaulting to wait.", action, sessionId);
				}
				return true;

			case AiConstants.EVENT_QUESTION:
				Long eventFixedQId = dataNode.path("fixed_q_id").asLong();
				String qType = dataNode.path("q_type").asText();
				String questionText = dataNode.path("question_text").asText();
				int turnNum = dataNode.path("turn_num").asInt();
				QuestionPayload fixedQuestionPayload = QuestionPayload.of(eventFixedQId, qType, questionText, turnNum,
					order, totalQuestions);
				return sseEmitterService.send(sessionId, AiConstants.EVENT_QUESTION, fixedQuestionPayload);

			case AiConstants.EVENT_ANALYZE_ANSWER:
				String actionResult = dataNode.path("action").asText();
				String analysis = dataNode.path("analysis").asText();

				nextAction.set(actionResult);
				log.info("Analysis result - action: {}, analysis: {}", actionResult, analysis);
				return true;

			case AiConstants.EVENT_TOKEN:
			case AiConstants.EVENT_CONTINUE:
				String content = dataNode.path("content").asText();
				qType = dataNode.path("q_type").asText("TAIL");

				if ("RETRY".equals(qType)) {} else {
					tailQuestionGenerated.set(true);
				}

				QuestionPayload questionPayload = QuestionPayload.of(
					fixedQuestionId,
					qType,
					content,
					nextTurnNum,
					order,
					totalQuestions);
				return sseEmitterService.send(sessionId, AiConstants.EVENT_CONTINUE, questionPayload);

			case AiConstants.EVENT_GENERATE_TAIL_COMPLETE:
				tailQuestionGenerated.set(true);
				String tailQuestionText = dataNode.path("message").asText();
				int tailQuestionCount = dataNode.path("tail_question_count").asInt();
				interviewService.saveTailQuestionLog(sessionId, fixedQuestionId, tailQuestionText, tailQuestionCount);
				log.info("Tail question saved - sessionId: {}, fixedQuestionId: {}, count: {}", sessionId,
					fixedQuestionId,
					tailQuestionCount);

				QuestionPayload tailCompletePayload = QuestionPayload.of(fixedQuestionId, "TAIL", tailQuestionText,
					nextTurnNum, order, totalQuestions);
				return sseEmitterService.send(sessionId, AiConstants.EVENT_GENERATE_TAIL_COMPLETE, tailCompletePayload);

			case AiConstants.EVENT_INTERVIEW_COMPLETE:
				StatusPayload completePayload = StatusPayload.builder().status("completed").build();
				boolean sent = sseEmitterService.send(sessionId, AiConstants.EVENT_INTERVIEW_COMPLETE, completePayload);
				sseEmitterService.complete(sessionId);
				return sent;

			case AiConstants.EVENT_ERROR:
				String errMessage = dataNode.path("message").asText();
				ErrorPayload errorPayload = ErrorPayload.builder().message(errMessage).build();
				return sseEmitterService.send(sessionId, eventType, errorPayload);

			case AiConstants.EVENT_REACTION:
				String reactionText = dataNode.path("reaction_text").asText();
				ReactionPayload reactionPayload = ReactionPayload.builder().reactionText(reactionText).build();
				return sseEmitterService.send(sessionId, AiConstants.EVENT_REACTION, reactionPayload);

			case AiConstants.EVENT_RETRY_REQUEST:
				String retryMessage = dataNode.path("message").asText();

				// [FIX] 마지막 꼬리질문 단계(max - 1)라면 RETRY를 무시하고 강제 종료(SKIP)
				if (currentTailCount >= maxTailQuestions) {
					log.info(
						"✋ [RETRY SUPPRESSED] Max tail count reached ({}). Suppressing RETRY and moving to next. sessionId={}, msg={}",
						currentTailCount, sessionId, retryMessage);

					handleTailLimitExceeded(sessionId, fixedQuestionId);
					nextAction.set("LIMIT_EXCEEDED_HANDLED");
					return true;
				}

				interviewService.saveRetryQuestionLog(sessionId, fixedQuestionId, retryMessage);
				log.info("Saved RETRY question log: sessionId={}, fixedQuestionId={}, msg={}", sessionId,
					fixedQuestionId,
					retryMessage);

				QuestionPayload retryPayload = QuestionPayload.of(fixedQuestionId, "RETRY", retryMessage, nextTurnNum,
					order,
					totalQuestions);
				return sseEmitterService.send(sessionId, AiConstants.EVENT_RETRY_REQUEST, retryPayload);

			default:
				log.debug("Unknown event type received: {}", eventType);
				return true;
		}
	}

	private void sendNextQuestion(String sessionId, FixedQuestionResponse nextQuestion) {
		// surveyId와 총 질문 수 Fetch
		Long surveyId = interviewService.getSurveyIdBySession(sessionId);
		int totalQuestions = interviewService.getTotalQuestionCount(surveyId);

		QuestionPayload questionPayload = QuestionPayload.of(
			nextQuestion.fixedQId(),
			AiConstants.ACTION_FIXED,
			nextQuestion.qContent(),
			1,
			nextQuestion.qOrder(),
			totalQuestions);
		sseEmitterService.send(sessionId, AiConstants.EVENT_QUESTION, questionPayload);

		// [FIX] 고정 질문 전송 후 done 이벤트 전송 (클라이언트 스트림 종료 처리용)
		StatusPayload donePayload = StatusPayload.builder().status("completed").build();
		sseEmitterService.send(sessionId, AiConstants.EVENT_DONE, donePayload);
	}

	private void sendInterviewComplete(String sessionId) {
		try {
			// 세션 상태 완료로 변경
			interviewService.completeSession(sessionId);

			// 세션 완료 후 임베딩 요청 (비동기)
			triggerSessionEmbedding(sessionId);

			StatusPayload completePayload = StatusPayload.builder().status("completed").build();
			sseEmitterService.send(sessionId, AiConstants.EVENT_INTERVIEW_COMPLETE, completePayload);

			// 클라이언트가 이벤트를 수신할 시간을 확보하기 위해 잠시 대기
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

			sseEmitterService.complete(sessionId);
		} catch (IllegalStateException e) {
			// 이미 완료된 세션인 경우 무시 (중복 호출 방지)
			log.warn("⚠️ [INTERVIEW COMPLETE] Session already completed, skipping. sessionId={}, error={}",
				sessionId, e.getMessage());
		}
	}

	// 세션 완료 시 고정질문별로 그룹핑된 Q&A 데이터를 AI 서버에 임베딩 요청
	private void triggerSessionEmbedding(String sessionId) {
		try {
			Map<Long, List<InterviewLog>> logsByFixedQuestion = interviewService
				.getLogsGroupedByFixedQuestion(sessionId);

			Long surveyId = interviewService.getSurveyIdBySession(sessionId);
			// Survey UUID 조회
			Survey survey = surveyRepository.findById(surveyId)
				.orElseThrow(() -> new RuntimeException("Survey not found: " + surveyId));
			String surveyUuid = survey.getUuid().toString();

			logsByFixedQuestion.forEach((fixedQuestionId, logs) -> {
				List<SessionEmbeddingRequest.QaPair> qaPairs = logs.stream()
					.filter(l -> l.getAnswerText() != null)
					.map(l -> SessionEmbeddingRequest.QaPair.of(
						l.getQuestionText(),
						l.getAnswerText(),
						l.getType().name()))
					.toList();

				if (!qaPairs.isEmpty()) {
					// [NEW] Metadata 생성
					java.util.Map<String, Object> metadata = new java.util.HashMap<>();
					try {
						// surveySessionRepository를 통해 Session 조회
						com.playprobie.api.domain.interview.domain.SurveySession session = surveySessionRepository
							.findByUuid(java.util.UUID.fromString(sessionId))
							.orElse(null);

						if (session != null && session.getTesterProfile() != null) {
							com.playprobie.api.domain.interview.domain.TesterProfile profile = session
								.getTesterProfile();
							if (profile.getGender() != null)
								metadata.put("gender", profile.getGender());
							if (profile.getAgeGroup() != null)
								metadata.put("age_group", profile.getAgeGroup());
							if (profile.getPreferGenre() != null)
								metadata.put("prefer_genre", profile.getPreferGenre());
						}
					} catch (Exception e) {
						log.warn("⚠️ Metadata extraction failed for session: {}", sessionId, e);
					}

					// === 수정: 모든 로그 중 최고 품질 사용 (꼬리질문으로 품질 개선 반영) ===
					String validity = null;
					String quality = null;

					// 1. validity는 FIXED 답변에서 추출
					InterviewLog fixedLog = logs.stream()
						.filter(l -> l.getType() == QuestionType.FIXED)
						.findFirst()
						.orElse(null);

					if (fixedLog != null && fixedLog.getAnalysis() != null) {
						validity = fixedLog.getAnalysis().getValidity() != null
							? fixedLog.getAnalysis().getValidity().name()
							: null;
					}

					// 2. quality는 모든 로그 중 최고 품질 선택 (FULL > GROUNDED > FLOATING > EMPTY)
					com.playprobie.api.domain.interview.domain.AnswerQuality bestQuality = logs.stream()
						.filter(l -> l.getAnalysis() != null && l.getAnalysis().getQuality() != null)
						.map(l -> l.getAnalysis().getQuality())
						.max(java.util.Comparator.comparingInt(Enum::ordinal))
						.orElse(null);

					if (bestQuality != null) {
						quality = bestQuality.name();
					}

					SessionEmbeddingRequest request = SessionEmbeddingRequest.builder()
						.sessionId(sessionId)
						.surveyUuid(surveyUuid) // surveyUuid 사용
						.fixedQuestionId(fixedQuestionId)
						.qaPairs(qaPairs)
						.metadata(metadata)
						.autoTriggerAnalysis(true)
						// === 추가: Quality Metadata ===
						.validity(validity)
						.quality(quality)
						.build();

					// Embedding 요청 후 analysis 자동 트리거
					embedSessionData(request, surveyUuid, fixedQuestionId).subscribe();
				}
			});
		} catch (Exception e) {
			log.error("Failed to trigger session embedding for sessionId: {}", sessionId, e);
		}
	}

	@Override
	public Mono<SessionEmbeddingResponse> embedSessionData(SessionEmbeddingRequest request) {
		return embedSessionData(request, request.surveyUuid(), request.fixedQuestionId());
	}

	private Mono<SessionEmbeddingResponse> embedSessionData(SessionEmbeddingRequest request, String surveyUuid,
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
						triggerAnalysis(surveyUuid, fixedQuestionId);
					} else {
						log.info("⏭️ Question {} Auto-trigger analysis skipped", fixedQuestionId);
					}
				})
			.doOnError(
				error -> log.error("❌ Embedding failed for session: {}, fixedQId: {}, error: {}",
					request.sessionId(), fixedQuestionId, error.getMessage(), error));
	}

	@Override
	public void triggerAnalysis(String surveyUuid, Long fixedQuestionId) {
		log.info("triggerAnalysis called, publishing event for: {}", fixedQuestionId);
		eventPublisher.publishEvent(
			new AnalysisTriggerEvent(surveyUuid, fixedQuestionId));
	}

	@Override
	public Flux<ServerSentEvent<String>> streamQuestionAnalysis(String surveyUuid, Long fixedQuestionId,
		Map<String, String> filters) {
		// AI 서비스는 snake_case 키를 사용하므로 변환 (ageGroup → age_group, preferGenre →
		// prefer_genre)
		Map<String, String> convertedFilters = null;
		if (filters != null) {
			convertedFilters = new java.util.HashMap<>();
			for (Map.Entry<String, String> entry : filters.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				if (value == null || value.isBlank())
					continue;

				// camelCase → snake_case 변환
				switch (key) {
					case "ageGroup" -> convertedFilters.put("age_group", value);
					case "preferGenre" -> convertedFilters.put("prefer_genre", value);
					default -> convertedFilters.put(key, value); // gender 등은 그대로
				}
			}
		}

		QuestionAnalysisRequest request = QuestionAnalysisRequest.builder()
			.surveyUuid(surveyUuid)
			.fixedQuestionId(fixedQuestionId)
			.filters(convertedFilters)
			.build();

		return aiWebClient.post()
			.uri("/analytics/questions/{questionId}", fixedQuestionId)
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.TEXT_EVENT_STREAM)
			.bodyValue(request)
			.retrieve()
			.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
			.map(sse -> {
				// data만 꺼내서 새로운 SSE 생성 (event 타입 유지)
				String event = sse.event() != null ? sse.event() : "message";
				String data = sse.data() != null ? (String)sse.data() : "";
				return ServerSentEvent.builder(data).event(event).build();
			});
	}

	@Override
	public Mono<String> generateSurveySummary(List<String> questionSummaries) {
		log.info("📝 설문 종합 평가 요청: {}개 질문", questionSummaries.size());

		SurveySummaryRequest request = new SurveySummaryRequest(questionSummaries);

		return aiWebClient.post()
			.uri("/analytics/survey/summary")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request)
			.retrieve()
			.bodyToMono(SurveySummaryResponse.class)
			.map(SurveySummaryResponse::surveySummary)
			.doOnSuccess(summary -> log.info("✅ 설문 종합 평가 완료: {}", summary))
			.doOnError(e -> log.error("❌ 설문 종합 평가 실패", e))
			.onErrorReturn("");
	}

	// 꼬리질문 제한 초과 시 사용할 기본 리액션 메시지들
	private static final String[] TAIL_LIMIT_REACTIONS = {
		"좋은 의견 감사해요! 다음 주제로 넘어가볼게요 😊",
		"충분히 이해했어요! 다른 부분도 여쭤볼게요",
		"네, 잘 알겠어요! 그럼 다음 질문 드릴게요",
		"좋아요, 이 부분은 충분한 것 같아요! 다음으로 넘어갈게요"
	};

	/**
	 * 꼬리질문 횟수 제한 초과 시 호출
	 * AI 호출 없이 바로 다음 고정 질문으로 이동
	 * 중간 질문 전환 시에만 리액션 전송 (마지막→엔딩 시에는 리액션 없음)
	 */
	private void handleTailLimitExceeded(String sessionId, Long fixedQuestionId) {
		// 다음 고정 질문 확인
		FixedQuestionResponse currentQuestion = interviewService.getQuestionById(fixedQuestionId);
		int currentOrder = currentQuestion.qOrder();
		var nextQuestionOpt = interviewService.getNextQuestion(sessionId, currentOrder);

		// 다음 질문이 있을 때만 리액션 전송 (마지막→엔딩일 때는 리액션 없이 바로 종료)
		if (nextQuestionOpt.isPresent()) {
			String reactionText = TAIL_LIMIT_REACTIONS[(int)(Math.random() * TAIL_LIMIT_REACTIONS.length)];
			ReactionPayload reactionPayload = ReactionPayload.builder().reactionText(reactionText).build();
			sseEmitterService.send(sessionId, AiConstants.EVENT_REACTION, reactionPayload);
			log.info("🎭 [TAIL LIMIT REACTION] sessionId={}, reaction={}", sessionId, reactionText);
		}

		// done 이벤트를 클라이언트로 전송
		StatusPayload donePayload = StatusPayload.builder().status("tail_limit_exceeded").build();
		sseEmitterService.send(sessionId, AiConstants.EVENT_DONE, donePayload);

		// 다음 고정 질문 발송 또는 종료 (이미 조회한 nextQuestionOpt 활용)
		nextQuestionOpt.ifPresentOrElse(
			nextQuestion -> sendNextQuestion(sessionId, nextQuestion),
			() -> proceedToClosingOrInsight(sessionId, AiConstants.REASON_ALL_DONE)); // 클로징 전 인사이트 체크
	}

	/**
	 * 클로징 전 인사이트 질문 체크
	 * 인사이트 태그가 있으면 인사이트 질문 Phase로 진입, 없으면 바로 클로징
	 */
	private void proceedToClosingOrInsight(String sessionId, String endReason) {
		try {
			if (insightQuestionService.hasUnaskedInsights(sessionId)) {
				log.info("🔍 [INSIGHT CHECK] Found unasked insights. Starting insight phase. sessionId={}", sessionId);
				boolean started = insightQuestionService.startInsightQuestionPhase(sessionId);
				if (started) {
					return; // 인사이트 질문 Phase로 진행
				}
			}
		} catch (Exception e) {
			log.warn("⚠️ [INSIGHT CHECK] Error checking insights, proceeding to closing. sessionId={}, error={}",
				sessionId, e.getMessage());
		}
		// 인사이트 없으면 바로 클로징
		streamClosing(sessionId, endReason);
	}

	// ========== 세션 Opening/Closing 방법 ==========

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
			.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});

		eventStream.subscribe(
			sse -> handleOpeningEvent(sessionId, sse.data()),
			error -> {
				log.error("Error in streamOpening: {}", error.getMessage());
				sseEmitterService.send(sessionId, AiConstants.EVENT_ERROR, "오프닝 생성 오류");
			},
			() -> log.info("Opening stream completed for sessionId: {}", sessionId));
	}

	/**
	 * AI 서버에 세션 종료(클로징) 요청을 보내고 SSE 스트리밍 응답을 클라이언트로 전달합니다.
	 * Phase 5: 마무리 멘트 생성 후 인터뷰 완료 처리
	 */
	public void streamClosing(String sessionId, String endReason) {
		log.info("🎬 [CLOSING START] Requesting closing remarks from AI. sessionId={}, reason={}", sessionId,
			endReason);

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
			.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});

		eventStream.subscribe(
			sse -> handleClosingEvent(sessionId, sse.data()),
			error -> {
				log.error("❌ [CLOSING ERROR] FastAPI error during closing: sessionId={}, error={}",
					sessionId, error.getMessage(), error);
				sendInterviewComplete(sessionId);
			},
			() -> log.info("✅ [CLOSING STREAM COMPLETE] AI closing stream finished. sessionId={}", sessionId));
	}

	private void handleOpeningEvent(String sessionId, String jsonStr) {
		try {
			JsonNode rootNode = objectMapper.readTree(jsonStr);
			String eventType = rootNode.path("event").asText();
			JsonNode dataNode = rootNode.path("data");

			switch (eventType) {
				case AiConstants.EVENT_START:
					StatusPayload startPayload = StatusPayload.builder()
						.status(dataNode.path("status").asText()).build();
					sseEmitterService.send(sessionId, AiConstants.EVENT_START, startPayload);
					break;

				// ===== 인사말 스트리밍 (새 이벤트) =====
				case AiConstants.EVENT_GREETING_CONTINUE:
					String greetingToken = dataNode.path("content").asText();
					QuestionPayload greetingPayload = QuestionPayload.of(null, "GREETING", greetingToken, 0, null,
						null);
					sseEmitterService.send(sessionId, AiConstants.EVENT_GREETING_CONTINUE, greetingPayload);
					break;

				// ===== 인사말 완료 → 첫번째 고정질문 전송 =====
				case AiConstants.EVENT_GREETING_DONE:
					log.info("👋 [GREETING DONE] Sending first fixed question. sessionId={}", sessionId);
					// DB에서 첫번째 고정질문 조회
					FixedQuestionResponse firstQuestion = interviewService.getFirstQuestion(sessionId);
					Long surveyId = interviewService.getSurveyIdBySession(sessionId);
					int totalQs = interviewService.getTotalQuestionCount(surveyId);

					QuestionPayload questionPayload = QuestionPayload.of(
						firstQuestion.fixedQId(),
						AiConstants.ACTION_FIXED,
						firstQuestion.qContent(),
						1,
						firstQuestion.qOrder(),
						totalQs);
					sseEmitterService.send(sessionId, AiConstants.EVENT_QUESTION, questionPayload);

					// [FIX] 첫번째 고정 질문 전송 후 done 이벤트 전송
					StatusPayload firstDonePayload = StatusPayload.builder().status("completed").build();
					sseEmitterService.send(sessionId, AiConstants.EVENT_DONE, firstDonePayload);
					break;

				// ===== 레거시 호환: 기존 continue 이벤트 =====
				case AiConstants.EVENT_CONTINUE:
					String content = dataNode.path("content").asText();
					QuestionPayload openingPayload = QuestionPayload.of(null, AiConstants.ACTION_OPENING, content, 0,
						null, null);
					sseEmitterService.send(sessionId, AiConstants.EVENT_CONTINUE, openingPayload);
					break;

				case AiConstants.EVENT_DONE:
					String questionText = dataNode.path("question_text").asText();
					QuestionPayload donePayload = QuestionPayload.of(null, AiConstants.ACTION_OPENING, questionText, 0,
						null, null);
					sseEmitterService.send(sessionId, AiConstants.EVENT_DONE, donePayload);
					break;

				case AiConstants.EVENT_ERROR:
					String errMsg = dataNode.path("message").asText();
					sseEmitterService.send(sessionId, AiConstants.EVENT_ERROR,
						ErrorPayload.builder().message(errMsg).build());
					break;
			}

		} catch (JsonProcessingException e) {
			log.error("Failed to parse opening event: {}", e.getMessage());
		}
	}

	private void handleClosingEvent(String sessionId, String jsonStr) {
		log.debug("📥 [CLOSING EVENT RAW] sessionId={}, json={}", sessionId, jsonStr);
		try {
			JsonNode rootNode = objectMapper.readTree(jsonStr);
			String eventType = rootNode.path("event").asText();
			JsonNode dataNode = rootNode.path("data");
			log.info("🎭 [CLOSING EVENT] sessionId={}, eventType={}", sessionId, eventType);

			switch (eventType) {
				case AiConstants.EVENT_START:
					log.info("▶️ [CLOSING START EVENT] Sending start event to client. sessionId={}", sessionId);
					StatusPayload startPayload = StatusPayload.builder()
						.status(dataNode.path("status").asText()).build();
					sseEmitterService.send(sessionId, AiConstants.EVENT_START, startPayload);
					break;

				case AiConstants.EVENT_CONTINUE:
					String content = dataNode.path("content").asText();
					log.info("💬 [CLOSING CONTENT] Streaming closing remarks. sessionId={}, contentLength={}",
						sessionId, content.length());
					QuestionPayload questionPayload = QuestionPayload.of(null, AiConstants.ACTION_CLOSING, content, 0,
						null, null);
					sseEmitterService.send(sessionId, AiConstants.EVENT_CONTINUE, questionPayload);
					break;

				case AiConstants.EVENT_DONE:
					log.info("🏁 [CLOSING DONE] Closing remarks complete. Finalizing interview. sessionId={}",
						sessionId);
					// 마무리 멘트 전송 후 인터뷰 완료 처리
					sendInterviewComplete(sessionId);
					break;

				case AiConstants.EVENT_ERROR:
					String errMsg = dataNode.path("message").asText();
					log.error("❌ [CLOSING ERROR EVENT] AI returned error. sessionId={}, error={}", sessionId, errMsg);
					sseEmitterService.send(sessionId, AiConstants.EVENT_ERROR,
						ErrorPayload.builder().message(errMsg).build());
					sendInterviewComplete(sessionId);
					break;

				default:
					log.warn("⚠️ [UNKNOWN CLOSING EVENT] eventType={}, sessionId={}", eventType, sessionId);
			}
		} catch (JsonProcessingException e) {
			log.error("❌ [CLOSING PARSE ERROR] Failed to parse closing event. sessionId={}, error={}",
				sessionId, e.getMessage(), e);
			sendInterviewComplete(sessionId);
		}
	}

	/**
	 * 인사이트 Phase 완료 이벤트 핸들러
	 * 인사이트 질문 완료 후 클로징 진행
	 */
	@EventListener
	public void onInsightPhaseComplete(InsightPhaseCompleteEvent event) {
		log.info("🎯 [INSIGHT COMPLETE] Triggering closing after insight phase. sessionId={}", event.sessionUuid());
		streamClosing(event.sessionUuid(), AiConstants.REASON_ALL_DONE);
	}

	@Override
	public boolean checkHealth() {
		try {
			return Boolean.TRUE.equals(aiWebClient.get()
				.uri("/health")
				.retrieve()
				.toBodilessEntity()
				.map(response -> response.getStatusCode().is2xxSuccessful())
				.timeout(java.time.Duration.ofSeconds(3))
				.onErrorReturn(false)
				.block());
		} catch (Exception e) {
			log.debug("AI Server Health Check Failed: {}", e.getMessage());
			return false;
		}
	}
}
