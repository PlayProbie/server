package com.playprobie.api.domain.interview.api;

import java.io.IOException;
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
import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.dto.InterviewHistoryResponse;
import com.playprobie.api.domain.interview.dto.InterviewCreateResponse;
import com.playprobie.api.domain.interview.dto.UserAnswerResponse;
import com.playprobie.api.domain.interview.dto.UserAnswerRequest;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.global.domain.ApiResponse;
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
	private final SurveySessionRepository surveySessionRepository;

	@PostMapping("/interview/{surveyId}")
	public ResponseEntity<ApiResponse<InterviewCreateResponse>> createSession(
			@PathVariable Long surveyId) {
		return ResponseEntity.status(201).body(ApiResponse.of(interviewService.createSession(surveyId)));
	}

	@GetMapping("/interview/{surveyId}/{sessionUuid}")
	public ResponseEntity<ApiResponse<InterviewHistoryResponse>> selectInterviewList(
			@PathVariable Long surveyId,
			@PathVariable UUID sessionUuid) {
		return ResponseEntity.ok(ApiResponse.of(interviewService.getInterviewHistory(surveyId, sessionUuid)));
	}

	@GetMapping(value = "/interview/{sessionUuid}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@PathVariable UUID sessionUuid) throws IOException {
		return sseEmitterService.connect(sessionUuid);
	}

	@PostMapping("interview/{sessionUuid}/messages")
	public ResponseEntity<ApiResponse<UserAnswerResponse>> receiveAnswer(
			@PathVariable UUID sessionUuid,
			@RequestBody UserAnswerRequest request) {
		String sessionId = sessionUuid.toString();

		//TODO: fixed_question Key값을 얻기 위한 임시 조회
		FixedQuestionResponse currentQuestion = interviewService.getFirstQuestion(sessionId);

		// AI 스트리밍 시작
		fastApiClient.streamNextQuestion(sessionId, request);

		// 질문과 응답 저장
		UserAnswerResponse userAnswerResponse = interviewService.saveInterviewLog(sessionId, request, currentQuestion);

		return ResponseEntity.status(201).body(ApiResponse.of(userAnswerResponse));
	}
}
