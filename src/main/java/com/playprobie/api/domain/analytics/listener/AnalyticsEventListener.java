package com.playprobie.api.domain.analytics.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.playprobie.api.domain.analytics.application.AnalyticsSseService;
import com.playprobie.api.domain.analytics.event.AnalyticsUpdatedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AnalyticsEventListener {

	private final AnalyticsSseService analyticsSseService;

	@Async // SSE 전송이 본 로직(저장)을 차단하지 않도록 비동기 처리
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleAnalyticsUpdated(AnalyticsUpdatedEvent event) {
		analyticsSseService.notifyUpdate(event.getSurveyUuid());
	}
}
