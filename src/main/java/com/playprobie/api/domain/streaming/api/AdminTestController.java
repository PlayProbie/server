package com.playprobie.api.domain.streaming.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.streaming.application.StreamingResourceService;
import com.playprobie.api.domain.streaming.dto.ResourceStatusResponse;
import com.playprobie.api.domain.streaming.dto.TestActionResponse;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.global.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 테스트 Controller (Admin).
 * 
 * <p>
 * 테스트 시작/종료 및 상태 조회 기능을 제공합니다.
 */
@RestController
@RequestMapping("/surveys/{surveyId}/streaming-resource")
@RequiredArgsConstructor
public class AdminTestController {

    private final StreamingResourceService streamingResourceService;

    /**
     * 관리자 테스트를 시작합니다 (Capacity 0 → 1).
     * 
     * <p>
     * POST /surveys/{surveyId}/streaming-resource/start-test
     * 
     * @param surveyId Survey PK
     * @return 200 OK
     */
    @PostMapping("/start-test")
    public ResponseEntity<ApiResponse<TestActionResponse>> startTest(@PathVariable java.util.UUID surveyId,
            @AuthenticationPrincipal(expression = "user") User user) {
        TestActionResponse response = streamingResourceService.startTest(surveyId, user);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * 관리자 테스트를 종료합니다 (Capacity 1 → 0).
     * 
     * <p>
     * POST /surveys/{surveyId}/streaming-resource/stop-test
     * 
     * @param surveyId Survey PK
     * @return 200 OK
     */
    @PostMapping("/stop-test")
    public ResponseEntity<ApiResponse<TestActionResponse>> stopTest(@PathVariable java.util.UUID surveyId,
            @AuthenticationPrincipal(expression = "user") User user) {
        TestActionResponse response = streamingResourceService.stopTest(surveyId, user);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * 리소스 상태를 조회합니다 (Polling).
     * 
     * <p>
     * GET /surveys/{surveyId}/streaming-resource/status
     * 
     * @param surveyId Survey PK
     * @return 200 OK
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ResourceStatusResponse>> getStatus(@PathVariable java.util.UUID surveyId,
            @AuthenticationPrincipal(expression = "user") User user) {
        ResourceStatusResponse response = streamingResourceService.getResourceStatus(surveyId, user);
        return ResponseEntity.ok(ApiResponse.of(response));
    }
}
