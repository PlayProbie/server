package com.playprobie.api.domain.analytics.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.analytics.application.AnalyticsService;
import com.playprobie.api.domain.analytics.dto.AnalyticsResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics API", description = "설문 분석 결과 API")
public class AnalyticsController {

	private final AnalyticsService analyticsService;

	/**
	 * 설문 분석 결과 조회 (REST API)
	 * GET /api/analytics/{surveyUuid}
	 */
	@GetMapping("/{surveyUuid}")
	@Operation(summary = "설문 분석 결과 조회", description = "AI 분석 결과를 JSON으로 반환합니다.")
	public ResponseEntity<AnalyticsResponse> getSurveyAnalysis(
		@PathVariable
		UUID surveyUuid) {
		log.info("📊 분석 결과 조회 요청: surveyUuid={}", surveyUuid);

		AnalyticsResponse response = analyticsService.getSurveyAnalysis(surveyUuid);

		return ResponseEntity.ok(response);
	}
}
