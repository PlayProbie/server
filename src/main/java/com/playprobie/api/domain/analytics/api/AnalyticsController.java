package com.playprobie.api.domain.analytics.api;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.analytics.application.AnalyticsService;
import com.playprobie.api.domain.analytics.dto.QuestionResponseAnalysisWrapper;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

	private final AnalyticsService analyticsService;

	/**
	 * 설문 분석 결과 스트리밍 (SSE)
	 * GET /api/analytics/{surveyId}
	 */
	@GetMapping(value = "/{surveyId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<QuestionResponseAnalysisWrapper>> getSurveyAnalysis(
			@PathVariable Long surveyId) {

		return analyticsService.getSurveyAnalysis(surveyId)
				.map(data -> ServerSentEvent.builder(data).build());
	}
}
