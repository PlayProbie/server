package com.playprobie.api.domain.survey.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.interview.domain.SessionStatus;
import com.playprobie.api.domain.survey.application.SurveyResultService;
import com.playprobie.api.domain.survey.dto.SurveyResultDetailResponse;
import com.playprobie.api.domain.survey.dto.SurveyResultListResponse;
import com.playprobie.api.domain.survey.dto.SurveyResultSummaryResponse;
import com.playprobie.api.global.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/surveys/results")
@RequiredArgsConstructor
public class SurveyResultApi {

    private final SurveyResultService surveyResultService;

    @GetMapping("/{gameId}")
    public ResponseEntity<ApiResponse<SurveyResultSummaryResponse>> getSummary(
            @PathVariable Long gameId,
            @RequestParam(required = false, defaultValue = "COMPLETED") SessionStatus status) {
        return ResponseEntity.ok(ApiResponse.of(surveyResultService.getSummary(gameId, status)));
    }

    @GetMapping("/{gameId}/listup")
    public ResponseEntity<ApiResponse<SurveyResultListResponse>> getResponseList(
            @PathVariable Long gameId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(surveyResultService.getResponseList(gameId, cursor, size)));
    }

    @GetMapping("/{surveyId}/details/{sessionId}")
    public ResponseEntity<ApiResponse<SurveyResultDetailResponse>> getResponseDetails(
            @PathVariable Long surveyId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.of(surveyResultService.getResponseDetails(surveyId, sessionId)));
    }
}
