package com.playprobie.api.domain.analytics.event;

import java.util.UUID;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AnalyticsUpdatedEvent {
	private final UUID surveyUuid;
}
