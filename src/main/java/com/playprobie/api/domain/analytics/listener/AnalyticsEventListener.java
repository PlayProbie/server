package com.playprobie.api.domain.analytics.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.playprobie.api.domain.analytics.application.AnalyticsSseService;
import com.playprobie.api.domain.analytics.event.AnalyticsUpdatedEvent;

import lombok.RequiredArgsConstructor;
import com.playprobie.api.domain.analytics.application.AnalyticsService;
import com.playprobie.api.domain.analytics.event.AnalysisTriggerEvent;

@Component
@RequiredArgsConstructor
public class AnalyticsEventListener {

	private final AnalyticsSseService analyticsSseService;
	private final AnalyticsService analyticsService;

	@Async // SSE 전송이 본 로직(저장)을 차단하지 않도록 비동기 처리
	@org.springframework.context.event.EventListener
	public void handleAnalyticsUpdated(AnalyticsUpdatedEvent event) {
		analyticsSseService.notifyUpdate(event.getSurveyUuid());
	}

	@Async
	@org.springframework.context.event.EventListener
	public void handleAnalysisTrigger(AnalysisTriggerEvent event) {
		analyticsService.analyzeSingleQuestion(event.getSurveyUuidAsUUID(), event.getFixedQuestionId());
	}
}
