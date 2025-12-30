package com.playprobie.api.domain.interview.api;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.playprobie.api.domain.interview.application.InterviewService;
import com.playprobie.api.domain.interview.dao.InterviewLogRepository;
import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.domain.QuestionType;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.interview.dto.InterviewHistoryResponse;
import com.playprobie.api.domain.interview.dto.InterviewStartResponse;
import com.playprobie.api.domain.interview.dto.MessageAcceptResponse;
import com.playprobie.api.domain.interview.dto.MessageRequest;
import com.playprobie.api.global.domain.ApiResponse;
import com.playprobie.api.infra.sse.SseEmitterService;
import com.playprobie.api.infra.ai.AiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@RestController
public class InterviewApi {

	/* mocking server 용도 선언 */
	private final AiClient aiClient;

	private final SseEmitterService sseEmitterService;
	private final InterviewService interviewService;
	private final SurveySessionRepository surveySessionRepository;

	@PostMapping("/surveys/interview/{surveyId}")
	public ResponseEntity<ApiResponse<InterviewStartResponse>> createSession(@PathVariable Long surveyId) {
		return ResponseEntity.ok(ApiResponse.of(interviewService.createSession(surveyId)));
	}

	@GetMapping("/surveys/interview/{surveyId}/{sessionId}")
	public ResponseEntity<ApiResponse<InterviewHistoryResponse>> selectInterviewList(@PathVariable Long surveyId,
			@PathVariable Long sessionId) {
		return ResponseEntity.ok(ApiResponse.of(interviewService.getInterviewHistory(surveyId, sessionId)));
	}

	@GetMapping(value = "/interview/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@PathVariable String sessionId) throws IOException {
		return sseEmitterService.connect(sessionId);
	}

	@PostMapping("interview/{sessionId}/messages")
	public ResponseEntity<ApiResponse<MessageAcceptResponse>> receiveAnswer(
			@PathVariable String sessionId,
			@RequestBody MessageRequest request) {

		// TODO: 실제 구현 시 대기 중인 질문 조회 및 저장 로직 필요
		// 현재는 임시로 응답 생성
		String currentQuestion = "어느 지점에서 막혔나요?"; // 대기 중인 질문에서 조회

		// AI 스트리밍 시작
		aiClient.streamNextQuestion(sessionId, request.answerText(), currentQuestion);

		String seesionId = sessionId;
		long fixedQId = 10L;
		int turnNum = request.turnNum();
		QuestionType qType = QuestionType.TAIL;
		String questionText = currentQuestion;
		String answerText = request.answerText();
		int tokensUsed = 10;

		SurveySession surveySession =
			surveySessionRepository.findById(Long.valueOf(sessionId))
				.orElseThrow(() -> new RuntimeException("Session not found"));

		InterviewLog interviewLog = InterviewLog.builder()
			.session(surveySession)
			.fixedQuestionId(fixedQId)
			.turnNum(turnNum)
			.type(qType)
			.questionText(questionText)
			.answerText(answerText)
			.build();

		InterviewLog savedLog = interviewService.saveInterviewLog(interviewLog);

		// 저장된 로그 응답
		MessageAcceptResponse response = MessageAcceptResponse.of(
				savedLog.getTurnNum(),
				String.valueOf(savedLog.getType()),
				savedLog.getFixedQuestionId(),
				savedLog.getQuestionText(),
				savedLog.getAnswerText());

		return ResponseEntity.status(201).body(ApiResponse.of(response));
	}
}
