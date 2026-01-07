package com.playprobie.api.domain.survey.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.playprobie.api.domain.user.domain.User;

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
import com.playprobie.api.global.common.response.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/surveys/results")
@RequiredArgsConstructor
@Tag(name = "Survey Result API", description = "설문 결과 조회 API")
public class SurveyResultApi {

    private final SurveyResultService surveyResultService;

    @GetMapping("/{gameUuid}")
    @Operation(summary = "설문 결과 요약 조회", description = "게임별 설문 결과 요약 통계를 조회합니다.")
    public ResponseEntity<CommonResponse<SurveyResultSummaryResponse>> getSummary(
            @AuthenticationPrincipal(expression = "user") User user,
            @PathVariable java.util.UUID gameUuid,
            @RequestParam(required = false, defaultValue = "COMPLETED") SessionStatus status) {
        return ResponseEntity.ok(CommonResponse.of(surveyResultService.getSummary(gameUuid, status, user)));
    }

    @GetMapping("/{gameUuid}/listup")
    @Operation(summary = "설문 응답 목록 조회", description = "설문 응답 목록을 커서 기반 페이징으로 조회합니다.")
    public ResponseEntity<CommonResponse<SurveyResultListResponse>> getResponseList(
            @AuthenticationPrincipal(expression = "user") User user,
            @PathVariable java.util.UUID gameUuid,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(CommonResponse.of(surveyResultService.getResponseList(gameUuid, cursor, size, user)));
    }

    @GetMapping("/{surveyUuid}/details/{sessionUuid}")
    @Operation(summary = "설문 응답 상세 조회", description = "특정 세션의 설문 응답 상세 내용을 조회합니다.")
    public ResponseEntity<CommonResponse<SurveyResultDetailResponse>> getResponseDetails(
            @AuthenticationPrincipal(expression = "user") User user,
            @PathVariable java.util.UUID surveyUuid,
            @PathVariable java.util.UUID sessionUuid) {
        return ResponseEntity
                .ok(CommonResponse.of(surveyResultService.getResponseDetails(surveyUuid, sessionUuid, user)));
    }
}
