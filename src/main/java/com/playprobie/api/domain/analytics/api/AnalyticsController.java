package com.playprobie.api.domain.analytics.api;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.analytics.application.AnalyticsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/analytics")
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
	public Flux<ServerSentEvent<Object>> getSurveyAnalysis(
		@PathVariable
		Long surveyId) {
		log.info("📊 SSE 분석 요청 시작: surveyId={}", surveyId);

		return analyticsService.getSurveyAnalysis(surveyId)
			.doOnNext(data -> log.info("📤 SSE 데이터 전송: questionId={}", data.fixedQuestionId()))
			.map(data -> ServerSentEvent.builder()
				.event("message") // 클라이언트 onmessage와 매칭
				.data((Object)data)
				.build())
			// 스트림 완료 시 "complete" 이벤트 전송 (클라이언트가 정상 종료 인식)
			.concatWith(Flux.just(ServerSentEvent.builder()
				.event("complete")
				.data((Object)"done") // 데이터가 있어야 브라우저에서 이벤트가 정상 발생
				.build()))
			.doOnComplete(() -> log.info("✅ SSE 스트림 완료: surveyId={}", surveyId))
			.doOnError(e -> log.error("❌ SSE 스트림 에러: surveyId={}, error={}", surveyId, e.getMessage()));
	}
}
