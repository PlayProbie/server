package com.playprobie.api.domain.streaming.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.SessionStatus;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.domain.streaming.dto.SessionAvailabilityResponse;
import com.playprobie.api.domain.streaming.dto.SignalResponse;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.domain.SurveyStatus;
import com.playprobie.api.domain.survey.exception.SurveyNotFoundException;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;
import com.playprobie.api.infra.gamelift.GameLiftService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.gameliftstreams.model.StartStreamSessionResponse;

/**
 * WebRTC 세션 관리 서비스.
 *
 * <p>
 * 테스터 세션 생성, 시그널링, 종료를 담당합니다.
 *
 * <p>
 * <b>트랜잭션 최적화</b>: processSignal() 메서드는 AWS API 호출을
 * 트랜잭션 밖으로 분리하여 DB 커넥션 점유 시간을 최소화합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StreamingSessionManager {

	private final SurveyRepository surveyRepository;
	private final SurveySessionRepository surveySessionRepository;
	private final StreamingResourceRepository streamingResourceRepository;
	private final StreamingSessionStateService streamingSessionStateService;
	private final GameLiftService gameLiftService;

	/**
	 * 세션 가용성을 확인합니다.
	 *
	 * @param surveyUuid Survey UUID
	 * @return 세션 가용성 응답
	 */
	public SessionAvailabilityResponse checkSessionAvailability(UUID surveyUuid) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);

		// 설문 상태 확인
		if (survey.getStatus() != SurveyStatus.ACTIVE) {
			return SessionAvailabilityResponse.unavailable(surveyUuid, survey.getGame().getName());
		}

		// 리소스 확인
		StreamingResource resource = streamingResourceRepository.findBySurveyId(survey.getId())
			.orElse(null);

		if (resource == null || !resource.getStatus().isAvailable()) {
			return SessionAvailabilityResponse.unavailable(surveyUuid, survey.getGame().getName());
		}

		return SessionAvailabilityResponse.available(surveyUuid, survey.getGame().getName());
	}

	/**
	 * WebRTC 시그널링을 처리합니다.
	 *
	 * <p>
	 * <b>트랜잭션 최적화</b>: AWS API 호출을 트랜잭션 밖으로 분리하여
	 * DB 커넥션 점유 시간을 최소화합니다.
	 * <ol>
	 *   <li>Phase 1: 검증 및 세션 생성 (독립 트랜잭션)</li>
	 *   <li>Phase 2: AWS API 호출 (트랜잭션 외부)</li>
	 *   <li>Phase 3: 결과 저장 (독립 트랜잭션)</li>
	 * </ol>
	 *
	 * @param surveyUuid    Survey UUID
	 * @param signalRequest 시그널 요청 (Base64)
	 * @return 시그널 응답
	 */
	public SignalResponse processSignal(UUID surveyUuid, String signalRequest) {
		log.info("Processing signal for surveyUuid={}", surveyUuid);

		// Phase 1: 검증 및 세션 생성 (독립 트랜잭션)
		var preparation = streamingSessionStateService.prepareSignalSession(surveyUuid);

		try {
			// Phase 2: AWS StartStreamSession 호출 (트랜잭션 외부 - 최대 20초 소요 가능)
			StartStreamSessionResponse awsResponse = gameLiftService.startStreamSession(
				preparation.streamGroupId(),
				preparation.applicationId(),
				signalRequest);

			// Phase 3: AWS 세션 연결 (독립 트랜잭션)
			streamingSessionStateService.connectAwsSession(preparation.sessionId(), awsResponse.arn());

			log.info("Signal processed: sessionUuid={}, awsSessionArn={}",
				preparation.sessionUuid(), awsResponse.arn());

			return SignalResponse.of(awsResponse.signalResponse(), preparation.sessionUuid());

		} catch (Exception e) {
			// AWS 호출 실패 시 세션 정리
			log.error("AWS StartStreamSession failed for sessionId={}: {}",
				preparation.sessionId(), e.getMessage(), e);
			streamingSessionStateService.cleanupFailedSession(preparation.sessionId());
			throw e;
		}
	}

	/**
	 * 세션을 종료합니다.
	 *
	 * <p>
	 * <b>AWS 실패 처리</b>: AWS 세션 종료가 실패해도 DB 상태는 업데이트합니다.
	 * AWS 세션은 타임아웃으로 자동 종료되므로 DB 일관성이 더 중요합니다.
	 *
	 * @param surveyUuid        Survey UUID
	 * @param surveySessionUuid Session UUID
	 * @param reason            종료 사유
	 * @param proceedToInterview 인터뷰로 진행 여부
	 */
	@Transactional
	public void terminateSession(UUID surveyUuid, UUID surveySessionUuid, String reason, boolean proceedToInterview) {
		log.info("Terminating session: surveyUuid={}, sessionUuid={}, reason={}, proceedToInterview={}",
			surveyUuid, surveySessionUuid, reason, proceedToInterview);

		SurveySession session = surveySessionRepository.findByUuid(surveySessionUuid)
			.orElseThrow(() -> new BusinessException(ErrorCode.SURVEY_SESSION_NOT_FOUND));

		// 이미 종료된 세션인지 확인
		if (session.getStatus().isFinished()) {
			return;
		}

		// 리소스 조회
		StreamingResource resource = streamingResourceRepository.findBySurveyId(session.getSurvey().getId())
			.orElse(null);

		// AWS 세션 종료 시도 (실패해도 DB는 업데이트)
		if (resource != null && session.getAwsSessionId() != null) {
			try {
				gameLiftService.terminateStreamSession(
					resource.getAwsStreamGroupId(),
					session.getAwsSessionId());
			} catch (Exception e) {
				// AWS 세션 종료 실패 - 타임아웃으로 자동 종료되므로 로그만 남기고 계속 진행
				log.warn("AWS session termination failed. Session will auto-terminate on timeout. " +
					"sessionUuid={}, awsSessionId={}, error={}",
					surveySessionUuid, session.getAwsSessionId(), e.getMessage());
			}
		}

		// DB 상태는 항상 업데이트
		if (proceedToInterview) {
			session.disconnectStream(); // 세션 유지, 상태 IN_PROGRESS로 변경
		} else {
			session.terminate(); // 세션 완전 종료
		}

		surveySessionRepository.save(session);

		log.info("Session terminated: sessionUuid={}, nextState={}", surveySessionUuid, session.getStatus());
	}

	/**
	 * 세션 상태를 확인합니다 (Heartbeat).
	 *
	 * @param surveySessionUuid Session UUID
	 * @return 세션 활성 여부
	 */
	public boolean isSessionActive(UUID surveySessionUuid) {
		return surveySessionRepository.findByUuid(surveySessionUuid)
			.map(session -> session.getStatus() == SessionStatus.CONNECTED)
			.orElse(false);
	}
}
