package com.playprobie.api.domain.interview.dto;

import java.util.List;

import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.interview.dto.common.Excerpt;
import com.playprobie.api.domain.interview.dto.common.SessionInfo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InterviewHistoryResponse {
	private SessionInfo session;
	private List<Excerpt> excerptList;
	private String sseUrl;

	public static InterviewHistoryResponse assemble(SurveySession session, List<InterviewLog> logs) {
		return InterviewHistoryResponse.builder()
			.session(SessionInfo.from(session))
			.excerptList(logs.stream().map(Excerpt::from).toList())
			.sseUrl("/chat/sessions/" + session.getId() + "/stream")
			.build();
	}
}
