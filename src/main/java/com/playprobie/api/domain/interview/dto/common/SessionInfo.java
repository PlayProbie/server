package com.playprobie.api.domain.interview.dto.common;

import com.playprobie.api.domain.interview.domain.SurveySession;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SessionInfo {
	private Long sessionId;
	private Long surveyId;
	private String testerId;
	private String status;

	public static SessionInfo from(SurveySession entity) {
		return SessionInfo.builder()
			.sessionId(entity.getId())
			.surveyId(entity.getSurvey().getId())
			.testerId(entity.getTesterProfile().getTesterId())
			.status(entity.getStatus().name())
			.build();
	}
}
