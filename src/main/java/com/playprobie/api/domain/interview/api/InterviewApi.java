package com.playprobie.api.domain.interview.api;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.playprobie.api.domain.interview.application.InterviewService;
import com.playprobie.api.domain.interview.dto.InterviewHistoryResponse;
import com.playprobie.api.domain.interview.dto.InterviewStartResponse;
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

	@PostMapping("/surveys/interview/{surveyId}")
	public ResponseEntity<ApiResponse<InterviewStartResponse>> createSession(@PathVariable Long surveyId) {
		return ResponseEntity.ok(ApiResponse.of(interviewService.createSession(surveyId)));
	}

	@GetMapping("/surveys/interview/{surveyId}/{sessionId}")
	public ResponseEntity<ApiResponse<InterviewHistoryResponse>> selectInterviewList(@PathVariable Long surveyId, @PathVariable Long sessionId) {
		return ResponseEntity.ok(ApiResponse.of(interviewService.getInterviewHistory(surveyId, sessionId)));
	}

	@GetMapping(value = "interview/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@PathVariable String sessionId) throws IOException {
		return sseEmitterService.connect(sessionId);
	}

	@PostMapping("interview/sessions/{sessionId}/message")
	public ResponseEntity<ApiResponse<String>> receiveAnswer(@PathVariable String sessionId, @RequestBody Map<String, String> body) {
		String userMessage = body.get("message");
		aiClient.streamNextQuestion(sessionId, userMessage);
		return ResponseEntity.ok(ApiResponse.of("message received & ai triggered"));
	}
}
