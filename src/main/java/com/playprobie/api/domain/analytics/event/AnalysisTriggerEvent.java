package com.playprobie.api.domain.analytics.event;

import java.util.UUID;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AnalysisTriggerEvent {
	private final String surveyUuid;
	private final Long fixedQuestionId;

	public UUID getSurveyUuidAsUUID() {
		return UUID.fromString(surveyUuid);
	}
}
