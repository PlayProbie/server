package com.playprobie.api.domain.interview.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.interview.dto.common.Excerpt;
import com.playprobie.api.domain.interview.dto.common.SessionInfo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InterviewHistoryResponse {
	private SessionInfo session;
	private List<Excerpt> excerpts;
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
