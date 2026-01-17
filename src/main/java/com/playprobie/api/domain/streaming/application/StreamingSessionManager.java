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
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StreamingSessionManager {

	private final SurveyRepository surveyRepository;
	private final SurveySessionRepository surveySessionRepository;
	private final StreamingResourceRepository streamingResourceRepository;
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
	 * @param surveyUuid    Survey UUID
	 * @param signalRequest 시그널 요청 (Base64)
	 * @return 시그널 응답
	 */
	@Transactional
	public SignalResponse processSignal(UUID surveyUuid, String signalRequest) {
		log.info("Processing signal for surveyUuid={}", surveyUuid);

		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);

		// 리소스 조회
		StreamingResource resource = streamingResourceRepository.findBySurveyId(survey.getId())
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_AVAILABLE));

		if (!resource.getStatus().isAvailable()) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_AVAILABLE);
		}

		// SurveySession 생성
		SurveySession session = SurveySession.builder()
			.survey(survey)
			.build();
		surveySessionRepository.save(session);

		// AWS StartStreamSession 호출 (내부적으로 Polling 수행)
		StartStreamSessionResponse awsResponse = gameLiftService.startStreamSession(
			resource.getAwsStreamGroupId(),
			resource.getAwsApplicationId(),
			signalRequest);

		// 세션에 AWS 세션 ID 연결
		session.connect(awsResponse.arn());
		surveySessionRepository.save(session);

		log.info("Signal processed: sessionUuid={}, awsSessionArn={}", session.getUuid(), awsResponse.arn());

		return SignalResponse.of(awsResponse.signalResponse(), session.getUuid());
	}

	/**
	 * 세션을 종료합니다.
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

		// AWS 세션 종료
		if (resource != null && session.getAwsSessionId() != null) {
			gameLiftService.terminateStreamSession(
				resource.getAwsStreamGroupId(),
				session.getAwsSessionId());
		}

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
