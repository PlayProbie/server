package com.playprobie.api.domain.interview.dto.common;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.playprobie.api.domain.interview.domain.SurveySession;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SessionInfo {
	private Long sessionId;
	private UUID sessionUuid;
	private Long surveyId;
	private String status;

	public static SessionInfo from(SurveySession entity) {
		return SessionInfo.builder()
			.sessionId(entity.getId())
			.sessionUuid(entity.getUuid())
			.surveyId(entity.getSurvey().getId())
			.status(entity.getStatus().name())
			.build();
	}
}
