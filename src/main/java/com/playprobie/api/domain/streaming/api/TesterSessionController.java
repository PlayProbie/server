package com.playprobie.api.domain.streaming.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.streaming.application.StreamingResourceService;
import com.playprobie.api.domain.streaming.dto.SessionAvailabilityResponse;
import com.playprobie.api.domain.streaming.dto.SessionStatusResponse;
import com.playprobie.api.domain.streaming.dto.SignalRequest;
import com.playprobie.api.domain.streaming.dto.SignalResponse;
import com.playprobie.api.domain.streaming.dto.TerminateSessionRequest;
import com.playprobie.api.domain.streaming.dto.TerminateSessionResponse;
import com.playprobie.api.global.common.response.CommonResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Tester Session API", description = "테스터 세션 관리 API (WebRTC 시그널링, Heartbeat 등)")
public class TesterSessionController {

    private final StreamingResourceService streamingResourceService;

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
    @Operation(summary = "세션 가용성 확인", description = "세션 가용 여부를 확인합니다.")
    public ResponseEntity<CommonResponse<SessionAvailabilityResponse>> checkSession(@PathVariable UUID surveyUuid) {
        SessionAvailabilityResponse response = streamingResourceService.checkSessionAvailability(surveyUuid);
        return ResponseEntity.ok(CommonResponse.of(response));
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
    @Operation(summary = "WebRTC 시그널링", description = "WebRTC 시그널을 처리합니다.")
    public ResponseEntity<CommonResponse<SignalResponse>> signal(
            @PathVariable UUID surveyUuid,
            @Valid @RequestBody SignalRequest request) {

        SignalResponse response = streamingResourceService.processSignal(surveyUuid, request.signalRequest());
        return ResponseEntity.ok(CommonResponse.of(response));
    }

    /**
     * 세션 상태를 확인합니다 (Heartbeat).
     * 
     * <p>
     * GET /surveys/{surveyUuid}/session/status?survey_session_uuid={uuid}
     * 
     * @param surveyUuid        Survey UUID
     * @param surveySessionUuid Session UUID
     * @return 200 OK
     */
    @GetMapping("/session/status")
    @Operation(summary = "세션 상태 확인 (Heartbeat)", description = "세션 활성 상태를 확인합니다.")
    public ResponseEntity<CommonResponse<SessionStatusResponse>> getSessionStatus(
            @PathVariable UUID surveyUuid,
            @RequestParam(name = "survey_session_uuid") UUID surveySessionUuid) {

        boolean isActive = streamingResourceService.isSessionActive(surveySessionUuid);
        return ResponseEntity.ok(CommonResponse.of(isActive
                ? SessionStatusResponse.active(surveySessionUuid)
                : SessionStatusResponse.inactive()));
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
    @Operation(summary = "세션 종료", description = "세션을 종료합니다.")
    public ResponseEntity<CommonResponse<TerminateSessionResponse>> terminateSession(
            @PathVariable UUID surveyUuid,
            @Valid @RequestBody TerminateSessionRequest request) {

        streamingResourceService.terminateSession(surveyUuid, request.surveySessionUuid(), request.reason());
        return ResponseEntity.ok(CommonResponse.of(TerminateSessionResponse.ok()));
    }
}
