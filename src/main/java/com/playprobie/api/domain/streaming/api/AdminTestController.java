package com.playprobie.api.domain.streaming.api;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.streaming.application.StreamingResourceService;
import com.playprobie.api.domain.streaming.dto.ResourceStatusResponse;
import com.playprobie.api.domain.streaming.dto.TestActionResponse;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.global.common.response.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/surveys/{surveyId}/streaming-resource")
@RequiredArgsConstructor
@Tag(name = "Admin Test API", description = "관리자 테스트 API (스트리밍 실행/중지)")
public class AdminTestController {

	private final StreamingResourceService streamingResourceService;

	@PostMapping("/start-test")
	@Operation(summary = "관리자 테스트 시작", description = "스트리밍 Capacity를 0 → 1로 설정하여 테스트를 시작합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "테스트 시작 성공"),
		@ApiResponse(responseCode = "429", description = "요청이 너무 많음 (Async Queue Full)")
	})
	public ResponseEntity<CommonResponse<TestActionResponse>> startTest(
		@PathVariable
		UUID surveyId,
		@AuthenticationPrincipal(expression = "user")
		User user) {

		TestActionResponse response = streamingResourceService.startTest(surveyId, user);

		return ResponseEntity.ok()
			.headers(buildAsyncHeaders(response))
			.body(CommonResponse.of(response));
	}

	@PostMapping("/stop-test")
	@Operation(summary = "관리자 테스트 종료", description = "스트리밍 Capacity를 1 → 0으로 설정하여 테스트를 종료합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "테스트 종료 성공"),
		@ApiResponse(responseCode = "429", description = "요청이 너무 많음 (Async Queue Full)")
	})
	public ResponseEntity<CommonResponse<TestActionResponse>> stopTest(
		@PathVariable
		UUID surveyId,
		@AuthenticationPrincipal(expression = "user")
		User user) {

		TestActionResponse response = streamingResourceService.stopTest(surveyId, user);

		return ResponseEntity.ok()
			.headers(buildAsyncHeaders(response))
			.body(CommonResponse.of(response));
	}

	@GetMapping("/status")
	@Operation(summary = "리소스 상태 조회", description = "GameLift 리소스 상태를 조회합니다.")
	public ResponseEntity<CommonResponse<ResourceStatusResponse>> getStatus(
		@PathVariable
		UUID surveyId,
		@AuthenticationPrincipal(expression = "user")
		User user) {
		ResourceStatusResponse response = streamingResourceService.getResourceStatus(surveyId, user);
		return ResponseEntity.ok(CommonResponse.of(response));
	}

	/**
	 * 비동기 처리 중인 응답에 대해 폴링 힌트 헤더를 생성합니다.
	 */
	private HttpHeaders buildAsyncHeaders(TestActionResponse response) {
		HttpHeaders headers = new HttpHeaders();
		if (response.isAsyncPending()) {
			headers.add("Retry-After", "5");
			if (response.requestId() != null) {
				headers.add("X-Request-ID", response.requestId().toString());
			}
		}
		return headers;
	}
}
