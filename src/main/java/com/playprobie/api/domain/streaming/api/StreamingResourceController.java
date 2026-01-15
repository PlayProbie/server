package com.playprobie.api.domain.streaming.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.streaming.application.StreamingResourceService;
import com.playprobie.api.domain.streaming.dto.CreateStreamingResourceRequest;
import com.playprobie.api.domain.streaming.dto.StreamingResourceResponse;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.global.common.response.CommonResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/surveys/{surveyId}/streaming-resource")
@RequiredArgsConstructor
@Tag(name = "Streaming Resource API", description = "스트리밍 리소스 관리 API (관리자용)")
public class StreamingResourceController {

	private final StreamingResourceService streamingResourceService;

	@PostMapping
	@Operation(summary = "스트리밍 리소스 할당 (비동기)", description = "GameLift 스트리밍 리소스를 할당합니다. " +
		"리소스 생성은 비동기로 진행되며, 응답 코드 202 Accepted와 함께 " +
		"Location 헤더에 포함된 URL을 통해 상태를 조회해야 합니다.")
	public ResponseEntity<CommonResponse<StreamingResourceResponse>> createResource(
		@AuthenticationPrincipal(expression = "user")
		User user,
		@PathVariable
		java.util.UUID surveyId,
		@Valid @RequestBody
		CreateStreamingResourceRequest request) {

		StreamingResourceResponse response = streamingResourceService.createResource(surveyId, request, user);

		return ResponseEntity
			.status(HttpStatus.ACCEPTED)
			.location(org.springframework.web.servlet.support.ServletUriComponentsBuilder
				.fromCurrentRequest()
				.build().toUri())
			.body(CommonResponse.of(response));
	}

	@GetMapping
	@Operation(summary = "스트리밍 리소스 조회", description = "할당된 스트리밍 리소스 정보를 조회합니다.")
	public ResponseEntity<CommonResponse<StreamingResourceResponse>> getResource(
		@AuthenticationPrincipal(expression = "user")
		User user,
		@PathVariable
		java.util.UUID surveyId) {
		StreamingResourceResponse response = streamingResourceService.getResourceByUuid(surveyId, user);
		return ResponseEntity.ok(CommonResponse.of(response));
	}

	@DeleteMapping
	@Operation(summary = "스트리밍 리소스 해제", description = "GameLift 스트리밍 리소스를 해제합니다.")
	public ResponseEntity<Void> deleteResource(
		@AuthenticationPrincipal(expression = "user")
		User user,
		@PathVariable
		java.util.UUID surveyId) {
		streamingResourceService.deleteResource(surveyId, user);
		return ResponseEntity.noContent().build();
	}
}
