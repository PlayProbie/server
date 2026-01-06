package com.playprobie.api.domain.interview.api;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.playprobie.api.domain.interview.application.InterviewService;
import com.playprobie.api.domain.interview.dto.InterviewCreateResponse;
import com.playprobie.api.domain.interview.dto.InterviewHistoryResponse;
import com.playprobie.api.domain.interview.dto.UserAnswerRequest;
import com.playprobie.api.domain.interview.dto.UserAnswerResponse;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.global.common.response.ApiResponse;
import com.playprobie.api.infra.ai.impl.FastApiClient;
import com.playprobie.api.infra.sse.service.SseEmitterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@RestController
public class InterviewApi {

	private final FastApiClient fastApiClient;
	private final SseEmitterService sseEmitterService;
	private final InterviewService interviewService;

	@PostMapping("/interview/{surveyUuid}")
	public ResponseEntity<ApiResponse<InterviewCreateResponse>> createSession(
			@PathVariable(name = "surveyUuid") java.util.UUID surveyUuid) {
		return ResponseEntity.status(201).body(ApiResponse.of(interviewService.createSession(surveyUuid)));
	}

	@GetMapping("/interview/{surveyUuid}/{sessionUuid}")
	public ResponseEntity<ApiResponse<InterviewHistoryResponse>> selectInterviewList(
			@PathVariable(name = "surveyUuid") java.util.UUID surveyUuid,
			@PathVariable(name = "sessionUuid") java.util.UUID sessionUuid) {
		return ResponseEntity.ok(ApiResponse.of(interviewService.getInterviewHistory(surveyUuid, sessionUuid)));
	}

	@GetMapping(value = "/interview/{sessionUuid}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@PathVariable UUID sessionUuid) {
		SseEmitter emitter = sseEmitterService.connect(sessionUuid);

		// SSE 연결 후 AI 오프닝 요청 (Phase 2: 인사말 + 오프닝 질문)
		String sessionId = sessionUuid.toString();

		// AI 서버에 세션 시작 요청 (비동기 SSE 스트리밍)
		// 게임 정보와 테스터 프로필은 InterviewService에서 조회하여 전달
		fastApiClient.streamOpening(sessionId, null, null);

		return emitter;
	}

	@PostMapping("interview/{sessionUuid}/messages")
	public ResponseEntity<ApiResponse<UserAnswerResponse>> receiveAnswer(
			@PathVariable UUID sessionUuid,
			@RequestBody UserAnswerRequest request) {
		String sessionId = sessionUuid.toString();

		// 클라이언트가 전송한 질문 ID로 질문 정보 조회
		FixedQuestionResponse currentQuestion = interviewService.getQuestionById(request.getFixedQId());

		// AI 스트리밍 시작
		fastApiClient.streamNextQuestion(sessionId, request);

		// 질문과 응답 저장
		UserAnswerResponse userAnswerResponse = interviewService.saveInterviewLog(sessionId, request, currentQuestion);

		return ResponseEntity.status(201).body(ApiResponse.of(userAnswerResponse));
	}
}
