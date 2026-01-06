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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics API", description = "설문 분석 결과 API")
public class AnalyticsController {

	private final AnalyticsService analyticsService;

	/**
	 * 설문 분석 결과 스트리밍 (SSE)
	 * GET /api/analytics/{surveyId}
	 */
	@GetMapping(value = "/{surveyId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "설문 분석 결과 스트리밍", description = "AI 분석 결과를 SSE로 스트리밍합니다.")
	public Flux<ServerSentEvent<QuestionResponseAnalysisWrapper>> getSurveyAnalysis(
			@PathVariable Long surveyId) {

		return analyticsService.getSurveyAnalysis(surveyId)
				.map(data -> ServerSentEvent.builder(data).build());
	}
}
