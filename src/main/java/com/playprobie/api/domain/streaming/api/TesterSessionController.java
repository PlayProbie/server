package com.playprobie.api.domain.streaming.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.streaming.application.StreamingResourceService;
import com.playprobie.api.domain.streaming.dto.SessionAvailabilityResponse;
import com.playprobie.api.domain.streaming.dto.SessionStatusResponse;
import com.playprobie.api.domain.streaming.dto.SignalRequest;
import com.playprobie.api.domain.streaming.dto.SignalResponse;
import com.playprobie.api.domain.streaming.dto.TerminateSessionRequest;
import com.playprobie.api.domain.streaming.dto.TerminateSessionResponse;
import com.playprobie.api.global.common.response.ApiResponse;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 테스터 세션 Controller (User/Tester).
 * 
 * <p>
 * ⚠️ 이 API는 {@code surveyUuid}를 사용합니다 (PK 노출 방지).
 * 
 * <p>
 * 세션 가용성 확인, WebRTC 시그널링, Heartbeat, 세션 종료 기능을 제공합니다.
 */
@RestController
@RequestMapping("/surveys/{surveyUuid}")
@RequiredArgsConstructor
public class TesterSessionController {

    private final StreamingResourceService streamingResourceService;
    private final SurveySessionRepository surveySessionRepository;

    /**
     * 세션 가용성을 확인합니다.
     * 
     * <p>
     * GET /surveys/{surveyUuid}/session
     * 
     * @param surveyUuid Survey UUID
     * @return 200 OK
     */
    @GetMapping("/session")
    public ResponseEntity<ApiResponse<SessionAvailabilityResponse>> checkSession(@PathVariable UUID surveyUuid) {
        SessionAvailabilityResponse response = streamingResourceService.checkSessionAvailability(surveyUuid);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * WebRTC 시그널링을 처리합니다.
     * 
     * <p>
     * POST /surveys/{surveyUuid}/signal
     * 
     * @param surveyUuid Survey UUID
     * @param request    시그널 요청
     * @return 200 OK
     */
    @PostMapping("/signal")
    public ResponseEntity<ApiResponse<SignalResponse>> signal(
            @PathVariable UUID surveyUuid,
            @Valid @RequestBody SignalRequest request) {

        SignalResponse response = streamingResourceService.processSignal(surveyUuid, request.signalRequest());
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * 세션 상태를 확인합니다 (Heartbeat).
     * 
     * <p>
     * GET /surveys/{surveyUuid}/session/status
     * 
     * @param surveyUuid Survey UUID
     * @return 200 OK
     */
    @GetMapping("/session/status")
    public ResponseEntity<ApiResponse<SessionStatusResponse>> getSessionStatus(@PathVariable UUID surveyUuid) {
        // TODO: 현재 세션 식별 로직 필요 (인증 연동)
        // 임시로 가장 최근 세션을 조회하는 방식 (실제로는 인증 정보 기반으로 조회해야 함)
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 세션을 종료합니다.
     * 
     * <p>
     * POST /surveys/{surveyUuid}/session/terminate
     * 
     * @param surveyUuid Survey UUID
     * @param request    종료 요청
     * @return 200 OK
     */
    @PostMapping("/session/terminate")
    public ResponseEntity<ApiResponse<TerminateSessionResponse>> terminateSession(
            @PathVariable UUID surveyUuid,
            @Valid @RequestBody TerminateSessionRequest request) {

        streamingResourceService.terminateSession(surveyUuid, request.surveySessionUuid(), request.reason());
        return ResponseEntity.ok(ApiResponse.of(TerminateSessionResponse.ok()));
    }
}
