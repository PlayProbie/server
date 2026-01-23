package com.playprobie.api.domain.replay.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.replay.application.InsightQuestionService;
import com.playprobie.api.domain.replay.application.ReplayService;
import com.playprobie.api.domain.replay.dto.InsightAnswerRequest;
import com.playprobie.api.domain.replay.dto.InsightAnswerResponse;
import com.playprobie.api.domain.replay.dto.PresignedUrlRequest;
import com.playprobie.api.domain.replay.dto.PresignedUrlResponse;
import com.playprobie.api.domain.replay.dto.ReplayLogRequest;
import com.playprobie.api.domain.replay.dto.UploadCompleteRequest;
import com.playprobie.api.global.common.response.CommonResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 리플레이 API Controller
 * 입력 로그 수신, Presigned URL 발급, 업로드 완료 처리, 인사이트 응답
 */
@Slf4j
@RestController
@RequestMapping("/sessions/{sessionId}/replay")
@RequiredArgsConstructor
public class ReplayController {

	private final ReplayService replayService;
	private final InsightQuestionService insightQuestionService;

	/**
	 * 입력 로그 배치 수신
	 * POST /sessions/{sessionId}/replay/logs
	 */
	@PostMapping("/logs")
	public ResponseEntity<Void> receiveInputLogs(
		@PathVariable("sessionId")
		UUID sessionId,
		@Valid @RequestBody
		ReplayLogRequest request) {
		log.info("[ReplayController] Received {} logs for session: {}",
			request.logs() != null ? request.logs().size() : 0, sessionId);

		replayService.processInputLogs(sessionId.toString(), request);

		return ResponseEntity.status(HttpStatus.ACCEPTED).build();
	}

	/**
	 * Presigned URL 발급
	 * POST /sessions/{sessionId}/replay/presigned-url
	 */
	@PostMapping("/presigned-url")
	public ResponseEntity<CommonResponse<PresignedUrlResponse>> generatePresignedUrl(
		@PathVariable("sessionId")
		UUID sessionId,
		@Valid @RequestBody
		PresignedUrlRequest request) {
		log.info("[ReplayController] Presigned URL request for session: {}, sequence: {}",
			sessionId, request.sequence());

		PresignedUrlResponse response = replayService.generatePresignedUrl(sessionId.toString(), request);

		return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.of(response));
	}

	/**
	 * 업로드 완료 알림
	 * POST /sessions/{sessionId}/replay/upload-complete
	 */
	@PostMapping("/upload-complete")
	public ResponseEntity<Void> completeUpload(
		@PathVariable("sessionId")
		UUID sessionId,
		@Valid @RequestBody
		UploadCompleteRequest request) {
		log.info("[ReplayController] Upload complete for session: {}, segment: {}",
			sessionId, request.segmentId());

		replayService.completeUpload(sessionId.toString(), request);

		return ResponseEntity.ok().build();
	}

	/**
	 * 인사이트 질문 답변 전송
	 * POST /sessions/{sessionId}/replay/insights/{tagId}/answer
	 */
	@PostMapping("/insights/{tagId}/answer")
	public ResponseEntity<CommonResponse<InsightAnswerResponse>> answerInsightQuestion(
		@PathVariable("sessionId")
		UUID sessionId,
		@PathVariable("tagId")
		Long tagId,
		@Valid @RequestBody
		InsightAnswerRequest request) {
		log.info("[ReplayController] Insight answer for session: {}, tagId: {}",
			sessionId, tagId);

		InsightAnswerResponse response = insightQuestionService.processInsightAnswerWithResponse(
			sessionId.toString(), tagId, request.answerText());

		return ResponseEntity.ok(CommonResponse.of(response));
	}
}
