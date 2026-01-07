package com.playprobie.api.domain.interview.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.playprobie.api.domain.interview.dto.common.SessionInfo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "인터뷰 생성 응답 DTO")
@Builder
@Getter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InterviewCreateResponse {

	@Schema(description = "생성된 세션 정보")
	private SessionInfo session;

	@Schema(description = "SSE 연결 URL", example = "/api/interviews/sse/550e8400-e29b-41d4-a716-446655440000")
	private String sseUrl;
}
