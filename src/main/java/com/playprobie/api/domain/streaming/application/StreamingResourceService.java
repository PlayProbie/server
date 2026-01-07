package com.playprobie.api.domain.streaming.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.dao.GameBuildRepository;
import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.game.exception.GameBuildNotFoundException;
import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.SessionStatus;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.model.StreamClass;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.domain.streaming.domain.StreamingResourceStatus;
import com.playprobie.api.domain.streaming.dto.CreateStreamingResourceRequest;
import com.playprobie.api.domain.streaming.dto.ResourceStatusResponse;
import com.playprobie.api.domain.streaming.dto.SessionAvailabilityResponse;
import com.playprobie.api.domain.streaming.dto.SignalResponse;
import com.playprobie.api.domain.streaming.dto.StreamingResourceResponse;
import com.playprobie.api.domain.streaming.dto.TestActionResponse;
import com.playprobie.api.domain.streaming.exception.StreamClassIncompatibleException;
import com.playprobie.api.domain.streaming.exception.StreamingResourceAlreadyExistsException;
import com.playprobie.api.domain.streaming.exception.StreamingResourceNotFoundException;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.domain.SurveyStatus;
import com.playprobie.api.domain.survey.exception.SurveyNotFoundException;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;
import com.playprobie.api.infra.gamelift.GameLiftService;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.application.WorkspaceSecurityManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.gameliftstreams.model.GetStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.StartStreamSessionResponse;
import software.amazon.awssdk.services.gameliftstreams.model.StreamGroupStatus;

/**
 * 스트리밍 리소스 관리 서비스.
 *
 * <p>
 * JIT Provisioning 워크플로우의 비즈니스 로직을 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StreamingResourceService {

	private final StreamingResourceRepository streamingResourceRepository;
	private final SurveyRepository surveyRepository;
	private final GameBuildRepository gameBuildRepository;
	private final SurveySessionRepository surveySessionRepository;
	private final GameLiftService gameLiftService;
	private final WorkspaceSecurityManager securityManager;
	private final StreamingProvisioner streamingProvisioner;

	// ========== Admin: Resource Management ==========

	/**
	 * 스트리밍 리소스를 할당합니다.
	 *
	 * @param surveyId Survey PK
	 * @param request  할당 요청
	 * @return 생성된 리소스 정보 (상태: CREATING)
	 */
	@Transactional
	public StreamingResourceResponse createResource(java.util.UUID surveyUuid,
		CreateStreamingResourceRequest request, User user) {
		log.info("Creating streaming resource for surveyUuid={}, buildId={}", surveyUuid, request.buildId());

		// 1. Survey 조회
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);

		// Security Check
		securityManager.validateWriteAccess(survey.getGame().getWorkspace(), user);

		Long surveyId = survey.getId();

		// 2. 중복 생성 방지
		validateResourceDuplication(surveyId);

		// 3. GameBuild 조회
		GameBuild build = gameBuildRepository.findByUuid(request.buildId())
			.orElseThrow(GameBuildNotFoundException::new);

		// 4. StreamClass 호환성 검증
		if (!StreamClass.isCompatible(request.instanceType(), build.getOsType())) {
			throw new StreamClassIncompatibleException(request.instanceType(), build.getOsType());
		}

		// 5. StreamingResource 생성 (CREATING 상태)
		StreamingResource resource = StreamingResource.builder()
			.survey(survey)
			.build(build)
			.instanceType(request.instanceType())
			.maxCapacity(request.maxCapacity())
			.build();

		streamingResourceRepository.save(resource);

		// 6. 비동기 프로비저닝 시작
		streamingProvisioner.provisionResourceAsync(resource.getId());

		log.info("Streaming resource creation initiated: resourceId={}, status={}",
			resource.getId(), resource.getStatus());

		return StreamingResourceResponse.from(resource);
	}

	private void validateResourceDuplication(Long surveyId) {
		streamingResourceRepository.findBySurveyId(surveyId).ifPresent(resource -> {
			if (!resource.getStatus().isTerminatedOrCleaning()) {
				throw new StreamingResourceAlreadyExistsException();
			}
		});
	}

	/**
	 * 스트리밍 리소스를 조회합니다.
	 *
	 * @param surveyId Survey PK
	 * @return 리소스 정보
	 */
	public StreamingResourceResponse getResource(Long surveyId, User user) {
		StreamingResource resource = streamingResourceRepository.findBySurveyId(surveyId)
			.orElseThrow(StreamingResourceNotFoundException::new);
		securityManager.validateReadAccess(resource.getSurvey().getGame().getWorkspace(), user);
		return StreamingResourceResponse.from(resource);
	}

	/**
	 * 스트리밍 리소스를 UUID로 조회합니다 (JIT 동기화 포함).
	 */
	@Transactional
	public StreamingResourceResponse getResourceByUuid(UUID uuid, User user) {
		StreamingResource resource = streamingResourceRepository.findBySurveyUuid(uuid)
			.orElseThrow(StreamingResourceNotFoundException::new);
		securityManager.validateReadAccess(resource.getSurvey().getGame().getWorkspace(), user);

		// JIT 상태 동기화 (Transitional State일 때만 수행)
		if (resource.getAwsStreamGroupId() != null && isTransitionalState(resource.getStatus())) {
			try {
				GetStreamGroupResponse awsResponse = gameLiftService.getStreamGroupStatus(
					resource.getAwsStreamGroupId());
				synchronizeState(resource, awsResponse.status());
			} catch (Exception e) {
				log.warn("Failed to sync status with AWS for resourceId={}: {}", resource.getId(),
					e.getMessage());
			}
		}

		return StreamingResourceResponse.from(resource);
	}

	/**
	 * 스트리밍 리소스를 삭제합니다.
	 *
	 * @param surveyId Survey PK
	 */
	@Transactional
	public void deleteResource(java.util.UUID surveyUuid, User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);
		deleteResource(survey.getId(), user);
	}

	@Transactional
	public void deleteResource(Long surveyId, User user) {
		log.info("Deleting streaming resource for surveyId={}", surveyId);

		StreamingResource resource = streamingResourceRepository.findBySurveyId(surveyId)
			.orElseThrow(StreamingResourceNotFoundException::new);

		securityManager.validateWriteAccess(resource.getSurvey().getGame().getWorkspace(), user);

		// AWS 리소스 삭제
		if (resource.getAwsStreamGroupId() != null) {
			gameLiftService.deleteStreamGroup(resource.getAwsStreamGroupId());
		}
		if (resource.getAwsApplicationId() != null) {
			gameLiftService.deleteApplication(resource.getAwsApplicationId());
		}

		resource.terminate();
		streamingResourceRepository.delete(resource);

		log.info("Streaming resource deleted: resourceId={}", resource.getId());
	}

	// ========== Admin: Test Management ==========

	/**
	 * 관리자 테스트를 시작합니다 (Capacity 0 → 1).
	 *
	 * @param surveyId Survey PK
	 * @return 테스트 상태
	 */
	@Transactional
	public TestActionResponse startTest(Long surveyId, User user) {
		log.info("Starting test for surveyId={}", surveyId);

		StreamingResource resource = streamingResourceRepository.findBySurveyId(surveyId)
			.orElseThrow(StreamingResourceNotFoundException::new);
		securityManager.validateWriteAccess(resource.getSurvey().getGame().getWorkspace(), user);

		// Capacity를 1로 변경
		gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), 1);

		resource.startTest();
		streamingResourceRepository.save(resource);

		log.info("Test started for resourceId={}", resource.getId());
		return TestActionResponse.startTest(resource.getStatus().name(), resource.getCurrentCapacity());
	}

	/**
	 * 설문을 활성화합니다 (Capacity 0 → Max Capacity).
	 */
	@Transactional
	public TestActionResponse activateResource(java.util.UUID surveyUuid, User user) {
		log.info("Activating resource for surveyUuid={}", surveyUuid);

		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);

		StreamingResource resource = streamingResourceRepository.findBySurveyId(survey.getId())
			.orElseThrow(StreamingResourceNotFoundException::new);
		securityManager.validateWriteAccess(resource.getSurvey().getGame().getWorkspace(), user);

		// Capacity를 maxCapacity로 변경
		gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), resource.getMaxCapacity());

		resource.activate(resource.getMaxCapacity());
		streamingResourceRepository.save(resource);

		log.info("Resource activated for resourceId={}, capacity={}", resource.getId(),
			resource.getMaxCapacity());
		return TestActionResponse.startTest("SCALING", resource.getMaxCapacity());
	}

	@Transactional
	public TestActionResponse startTest(java.util.UUID surveyUuid, User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);
		return startTest(survey.getId(), user);
	}

	/**
	 * 관리자 테스트를 종료합니다 (Capacity 1 → 0).
	 *
	 * @param surveyId Survey PK
	 * @return 테스트 상태
	 */
	@Transactional
	public TestActionResponse stopTest(Long surveyId, User user) {
		log.info("Stopping test for surveyId={}", surveyId);

		StreamingResource resource = streamingResourceRepository.findBySurveyId(surveyId)
			.orElseThrow(StreamingResourceNotFoundException::new);
		securityManager.validateWriteAccess(resource.getSurvey().getGame().getWorkspace(), user);

		// Capacity를 0으로 변경
		gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), 0);

		resource.stopTest();
		streamingResourceRepository.save(resource);

		log.info("Test stopped for resourceId={}", resource.getId());
		return TestActionResponse.stopTest(resource.getStatus().name(), resource.getCurrentCapacity());
	}

	@Transactional
	public TestActionResponse stopTest(java.util.UUID surveyUuid, User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);
		return stopTest(survey.getId(), user);
	}

	/**
	 * 리소스 상태를 조회합니다 (실시간 AWS API 호출).
	 *
	 * @param surveyId Survey PK
	 * @return 리소스 상태
	 */
	@Transactional
	public ResourceStatusResponse getResourceStatus(Long surveyId, User user) {
		StreamingResource resource = streamingResourceRepository.findBySurveyId(surveyId)
			.orElseThrow(StreamingResourceNotFoundException::new);
		securityManager.validateReadAccess(resource.getSurvey().getGame().getWorkspace(), user);

		// AWS API 호출 및 상태 동기화 (Transitional State일 때만 수행)
		boolean instancesReady = false;
		if (resource.getAwsStreamGroupId() != null && isTransitionalState(resource.getStatus())) {
			try {
				GetStreamGroupResponse awsResponse = gameLiftService.getStreamGroupStatus(
					resource.getAwsStreamGroupId());

				StreamGroupStatus awsStatus = awsResponse.status();
				instancesReady = awsStatus == StreamGroupStatus.ACTIVE;

				// AWS 상태에 따라 DB 동기화
				synchronizeState(resource, awsStatus);

			} catch (Exception e) {
				log.warn("Failed to sync status with AWS for resourceId={}: {}", resource.getId(),
					e.getMessage());
				// AWS 호출 실패 시 DB 상태 그대로 반환 (장애 전파 방지)
			}
		} else {
			// Stable State라면 DB 정보 기반으로 ready 여부 판단
			// ACTIVE, READY, TESTING 상태라면 인스턴스가 준비된 것으로 간주 가능하나,
			// 정확히는 ACTIVE 상태일 때만 실제 접속 가능
			instancesReady = resource.getStatus() == StreamingResourceStatus.ACTIVE;
		}

		return ResourceStatusResponse.of(
			resource.getStatus().name(),
			resource.getCurrentCapacity(),
			instancesReady);
	}

	private boolean isTransitionalState(StreamingResourceStatus status) {
		return status == StreamingResourceStatus.CREATING ||
			status == StreamingResourceStatus.PENDING ||
			status == StreamingResourceStatus.PROVISIONING ||
			status == StreamingResourceStatus.READY || // READY -> ACTIVE 가능성 (Start Test)
			status == StreamingResourceStatus.TESTING || // TESTING -> ACTIVE 가능성 (Activate)
			status == StreamingResourceStatus.SCALING;
	}

	// Throttling을 위한 마지막 업데이트 시간 저장소 (Key: ResourceID)
	private final java.util.Map<Long, java.time.LocalDateTime> lastCapacityUpdateMap = new java.util.concurrent.ConcurrentHashMap<>();

	private void synchronizeState(StreamingResource resource, StreamGroupStatus awsStatus) {
		if (awsStatus == StreamGroupStatus.ACTIVE) {

			// 1. Capacity 검증 (Capacity-Aware Logic)
			int desiredCapacity = resource.getMaxCapacity();
			int allocatedCapacity = 0;

			// Safe Collection Access
			try {
				GetStreamGroupResponse awsResponse = gameLiftService
					.getStreamGroupStatus(resource.getAwsStreamGroupId());
				if (awsResponse.locationStates() != null && !awsResponse.locationStates().isEmpty()) {
					// 기본 위치(첫 번째)의 할당 용량 확인
					// 실제로는 특정 리전(location)을 찾아야 할 수도 있으나, 현재는 단일 리전 가정
					allocatedCapacity = awsResponse.locationStates().get(0).allocatedCapacity();
				}
			} catch (Exception e) {
				log.warn("Failed to fetch location states for resourceId={}", resource.getId());
			}

			if (allocatedCapacity >= desiredCapacity) {
				// 2. 용량 확보됨 -> DB 상태를 ACTIVE로 동기화
				if (resource.getStatus() != StreamingResourceStatus.ACTIVE) {
					resource.activate(resource.getMaxCapacity());
					log.info("Resource verified as ACTIVE and READY (Capacity {}/{}) via sync logic. resourceId={}",
						allocatedCapacity, desiredCapacity, resource.getId());
				}
			} else {
				// 3. 용량 부족 (Mismatch) -> Self-Healing (재요청)
				log.warn("Capacity Mismatch detected! AWS Active but Allocated: {}, Desired: {}. resourceId={}",
					allocatedCapacity, desiredCapacity, resource.getId());

				triggerSelfHealing(resource);
			}

		} else if (awsStatus == StreamGroupStatus.ERROR) {
			if (resource.getStatus() != StreamingResourceStatus.ERROR) {
				resource.markError("AWS StreamGroup reported ERROR status");
				log.error("Resource verified as ERROR via sync logic. resourceId={}", resource.getId());
			}
		}
	}

	/**
	 * 용량 설정을 재요청합니다 (Throttling 적용).
	 */
	private void triggerSelfHealing(StreamingResource resource) {
		long resourceId = resource.getId();
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		java.time.LocalDateTime lastUpdate = lastCapacityUpdateMap.get(resourceId);

		// 쿨다운 체크 (60초)
		if (lastUpdate != null && java.time.Duration.between(lastUpdate, now).getSeconds() < 60) {
			log.info("Self-healing skipped due to cooldown. resourceId={}", resourceId);
			return;
		}

		try {
			log.info("Triggering self-healing: Re-applying capacity configuration. resourceId={}",
				resourceId);
			gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(),
				resource.getMaxCapacity());

			// 업데이트 성공 시 타임스탬프 갱신
			lastCapacityUpdateMap.put(resourceId, now);
		} catch (Exception e) {
			log.error("Self-healing failed for resourceId={}: {}", resourceId, e.getMessage());
		}
	}

	public ResourceStatusResponse getResourceStatus(java.util.UUID surveyUuid, User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);
		return getResourceStatus(survey.getId(), user);
	}

	// ========== Tester Session Management ==========

	/**
	 * 세션 가용성을 확인합니다.
	 *
	 * @param surveyUuid Survey UUID
	 * @return 세션 가용성
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

		// TODO: 실제 Capacity 대비 활성 세션 수 비교하여 가용성 판단
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

		// AWS StartStreamSession 호출
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
	 */
	@Transactional
	public void terminateSession(UUID surveyUuid, UUID surveySessionUuid, String reason) {
		log.info("Terminating session: surveyUuid={}, sessionUuid={}, reason={}",
			surveyUuid, surveySessionUuid, reason);

		SurveySession session = surveySessionRepository.findByUuid(surveySessionUuid)
			.orElseThrow(() -> new BusinessException(ErrorCode.SURVEY_SESSION_NOT_FOUND));

		// 이미 종료된 세션인지 확인
		if (session.getStatus().isTerminated()) {
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

		session.terminate();
		surveySessionRepository.save(session);

		log.info("Session terminated: sessionUuid={}", surveySessionUuid);
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
