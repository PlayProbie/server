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
import com.playprobie.api.infra.sse.dto.QuestionPayload;
import com.playprobie.api.infra.sse.service.SseEmitterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RequiredArgsConstructor
@RestController
@Tag(name = "Interview API", description = "인터뷰 진행 API (비회원 접근 가능)")
public class InterviewApi {

	private final FastApiClient fastApiClient;
	private final SseEmitterService sseEmitterService;
	private final InterviewService interviewService;

	@PostMapping("/interview/{surveyUuid}")
	@Operation(summary = "인터뷰 세션 생성", description = "설문 UUID로 새로운 인터뷰 세션을 생성합니다.")
	public ResponseEntity<ApiResponse<InterviewCreateResponse>> createSession(
			@PathVariable(name = "surveyUuid") java.util.UUID surveyUuid) {
		return ResponseEntity.status(201).body(ApiResponse.of(interviewService.createSession(surveyUuid)));
	}

	@GetMapping("/interview/{surveyUuid}/{sessionUuid}")
	@Operation(summary = "인터뷰 이력 조회", description = "세션의 인터뷰 대화 이력을 조회합니다.")
	public ResponseEntity<ApiResponse<InterviewHistoryResponse>> selectInterviewList(
			@PathVariable(name = "surveyUuid") java.util.UUID surveyUuid,
			@PathVariable(name = "sessionUuid") java.util.UUID sessionUuid) {
		return ResponseEntity.ok(ApiResponse.of(interviewService.getInterviewHistory(surveyUuid, sessionUuid)));
	}

	@GetMapping(value = "/interview/{sessionUuid}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "인터뷰 SSE 스트림 연결", description = "SSE를 통해 실시간 질문을 수신합니다.")
	public SseEmitter stream(@PathVariable UUID sessionUuid) {
		SseEmitter emitter = sseEmitterService.connect(sessionUuid);

		// SSE 연결 후 첫 질문 전송
		String sessionId = sessionUuid.toString();
		FixedQuestionResponse firstQuestion = interviewService.getFirstQuestion(sessionId);
		QuestionPayload questionPayload = QuestionPayload.of(
				firstQuestion.fixedQId(),
				"FIXED",
				firstQuestion.qContent(),
				firstQuestion.qOrder());
		sseEmitterService.send(sessionId, "question", questionPayload);

		return emitter;
	}

	@PostMapping("interview/{sessionUuid}/messages")
	@Operation(summary = "사용자 응답 전송", description = "사용자의 응답을 전송하고 AI 후속 질문을 스트리밍합니다.")
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
