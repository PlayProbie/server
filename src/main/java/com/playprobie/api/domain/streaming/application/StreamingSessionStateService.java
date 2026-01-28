package com.playprobie.api.domain.streaming.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.exception.SurveyNotFoundException;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 스트리밍 세션 상태 관리 서비스.
 *
 * <p>
 * <b>트랜잭션 최적화</b>: StreamingSessionManager에서 AWS API 호출을 트랜잭션 밖으로 분리하기 위해
 * 독립된 짧은 트랜잭션으로 DB 작업을 수행합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingSessionStateService {

	private final SurveyRepository surveyRepository;
	private final SurveySessionRepository surveySessionRepository;
	private final StreamingResourceRepository streamingResourceRepository;

	/**
	 * Signal 요청 전에 검증을 수행하고 초기 세션을 생성합니다.
	 *
	 * @param surveyUuid Survey UUID
	 * @return SignalPreparation 결과
	 * @throws BusinessException 리소스가 사용 불가능한 경우
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public SignalPreparation prepareSignalSession(UUID surveyUuid) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);

		// 리소스 조회 및 검증
		StreamingResource resource = streamingResourceRepository.findBySurveyId(survey.getId())
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_AVAILABLE));

		if (!resource.getStatus().isAvailable()) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_AVAILABLE);
		}

		// SurveySession 생성 (초기 상태)
		SurveySession session = SurveySession.builder()
			.survey(survey)
			.build();
		surveySessionRepository.save(session);

		log.debug("Signal session prepared: sessionUuid={}, resourceId={}",
			session.getUuid(), resource.getId());

		return new SignalPreparation(
			session.getId(),
			session.getUuid(),
			resource.getAwsStreamGroupId(),
			resource.getAwsApplicationId());
	}

	/**
	 * AWS 응답을 세션에 연결합니다.
	 *
	 * @param sessionId  세션 PK
	 * @param awsArn     AWS 세션 ARN
	 * @return 세션이 삭제된 경우 empty
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<UUID> connectAwsSession(Long sessionId, String awsArn) {
		Optional<SurveySession> sessionOpt = surveySessionRepository.findById(sessionId);
		if (sessionOpt.isEmpty()) {
			log.warn("Session deleted during AWS connection. sessionId={}", sessionId);
			return Optional.empty();
		}

		SurveySession session = sessionOpt.get();
		session.connect(awsArn);
		surveySessionRepository.save(session);

		log.debug("AWS session connected: sessionUuid={}, awsArn={}", session.getUuid(), awsArn);

		return Optional.of(session.getUuid());
	}

	/**
	 * AWS 호출 실패 시 세션을 정리합니다.
	 *
	 * @param sessionId 세션 PK
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void cleanupFailedSession(Long sessionId) {
		surveySessionRepository.findById(sessionId)
			.ifPresent(session -> {
				session.terminate();
				surveySessionRepository.save(session);
				log.debug("Failed session cleaned up: sessionId={}", sessionId);
			});
	}

	/**
	 * Signal 준비 결과를 담는 record.
	 */
	public record SignalPreparation(
		Long sessionId,
		UUID sessionUuid,
		String streamGroupId,
		String applicationId) {
	}
}
