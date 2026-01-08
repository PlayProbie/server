package com.playprobie.api.domain.interview.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.interview.dto.common.Excerpt;
import com.playprobie.api.domain.interview.dto.common.SessionInfo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "인터뷰 히스토리 응답 DTO")
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InterviewHistoryResponse {

	@Schema(description = "세션 정보")
	private SessionInfo session;

	@Schema(description = "인터뷰 발췌 목록")
	private List<Excerpt> excerpts;

	@Schema(description = "SSE 연결 URL", example = "/api/interviews/sse/550e8400-e29b-41d4-a716-446655440000")
	private String sseUrl;

	public static InterviewHistoryResponse assemble(
		SurveySession session,
		List<InterviewLog> logs,
		String sseUrl) {
		return InterviewHistoryResponse.builder()
			.session(SessionInfo.from(session))
			.excerpts(logs.stream().map(Excerpt::from).toList())
			.sseUrl(sseUrl)
			.build();
	}
}
