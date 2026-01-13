package com.playprobie.api.domain.streaming.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.streaming.dao.CapacityChangeRequestRepository;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.CapacityChangeRequest;
import com.playprobie.api.domain.streaming.domain.CapacityChangeType;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.domain.streaming.domain.StreamingResourceStatus;
import com.playprobie.api.domain.streaming.dto.TestActionResponse;
import com.playprobie.api.domain.streaming.exception.StreamingResourceNotFoundException;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.exception.SurveyNotFoundException;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.application.WorkspaceSecurityManager;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 테스트 및 활성화 관리 서비스.
 *
 * <p>
 * Start/Stop Test, Activate 기능을 담당합니다.
 * <b>트랜잭션 최적화</b>: AWS API 호출을 트랜잭션 밖으로 분리하여 DB 커넥션 고갈을 방지합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StreamingTestManager {

	private final StreamingResourceRepository streamingResourceRepository;
	private final SurveyRepository surveyRepository;
	private final CapacityChangeRequestRepository capacityChangeRequestRepository;
	private final CapacityChangeAsyncService capacityChangeAsyncService;
	private final WorkspaceSecurityManager securityManager;

	/**
	 * 관리자 테스트를 시작합니다 (Capacity 0 → 1).
	 *
	 * <p>
	 * Phase 1: DB 업데이트 (트랜잭션 내)
	 * Phase 2: 비동기 AWS API 호출 (트랜잭션 외)
	 *
	 * @param surveyUuid Survey UUID
	 * @param user       요청 사용자
	 * @return 테스트 액션 응답
	 */
	@Transactional
	public TestActionResponse startTest(UUID surveyUuid, User user) {
		log.info("Starting test: surveyUuid={}, user={}", surveyUuid, user.getEmail());

		StreamingResource resource = findResourceBySurveyUuid(surveyUuid, user, true);

		// 이미 테스트 중이면 현재 상태 반환
		if (resource.getStatus() == StreamingResourceStatus.TESTING) {
			return TestActionResponse.startTest("TESTING", 1);
		}

		// State Guard: 이미 스케일링 중이면 예외
		validateNotInTransition(resource);

		// Phase 1: DB Update & Intent Logging
		CapacityChangeRequest request = CapacityChangeRequest.create(
			resource, CapacityChangeType.START_TEST, 1);
		capacityChangeRequestRepository.save(request);

		resource.markScalingUp(1);
		streamingResourceRepository.save(resource);

		// Phase 2: Async Execution with Fail-Fast
		executeAsyncCapacityChange(resource.getId(), request.getId(), 1, CapacityChangeType.START_TEST);

		return TestActionResponse.inProgress("SCALING_UP", 1, request.getId());
	}

	/**
	 * 관리자 테스트를 종료합니다 (Capacity 1 → 0).
	 *
	 * @param surveyUuid Survey UUID
	 * @param user       요청 사용자
	 * @return 테스트 액션 응답
	 */
	@Transactional
	public TestActionResponse stopTest(UUID surveyUuid, User user) {
		log.info("Stopping test: surveyUuid={}, user={}", surveyUuid, user.getEmail());

		StreamingResource resource = findResourceBySurveyUuid(surveyUuid, user, true);

		// 이미 READY 상태면 현재 상태 반환
		if (resource.getStatus() == StreamingResourceStatus.READY) {
			return TestActionResponse.stopTest("READY", 0);
		}

		// State Guard
		validateNotInTransition(resource);

		// Phase 1: DB Update
		CapacityChangeRequest request = CapacityChangeRequest.create(
			resource, CapacityChangeType.STOP_TEST, 0);
		capacityChangeRequestRepository.save(request);

		resource.markScalingDown();
		streamingResourceRepository.save(resource);

		// Phase 2: Async Execution with Fail-Fast
		executeAsyncCapacityChange(resource.getId(), request.getId(), 0, CapacityChangeType.STOP_TEST);

		return TestActionResponse.inProgress("SCALING_DOWN", 0, request.getId());
	}

	/**
	 * 설문을 활성화합니다 (Capacity 0 → Max Capacity).
	 *
	 * <p>
	 * <b>트랜잭션 최적화</b>: 기존 동기 AWS API 호출 대신 비동기 패턴을 사용합니다.
	 *
	 * @param surveyUuid Survey UUID
	 * @param user       요청 사용자
	 * @return 테스트 액션 응답
	 */
	@Transactional
	public TestActionResponse activateResource(UUID surveyUuid, User user) {
		log.info("Activating resource for surveyUuid={}", surveyUuid);

		StreamingResource resource = findResourceBySurveyUuid(surveyUuid, user, true);

		// 이미 ACTIVE 상태면 현재 상태 반환
		if (resource.getStatus() == StreamingResourceStatus.ACTIVE) {
			return TestActionResponse.startTest("ACTIVE", resource.getCurrentCapacity());
		}

		// State Guard
		validateNotInTransition(resource);

		int targetCapacity = resource.getMaxCapacity();

		// Phase 1: DB Update & Intent Logging
		CapacityChangeRequest request = CapacityChangeRequest.create(
			resource, CapacityChangeType.ACTIVATE, targetCapacity);
		capacityChangeRequestRepository.save(request);

		resource.markScalingUp(targetCapacity);
		streamingResourceRepository.save(resource);

		// Phase 2: Async Execution with Fail-Fast
		executeAsyncCapacityChange(resource.getId(), request.getId(), targetCapacity, CapacityChangeType.ACTIVATE);

		log.info("Resource activation initiated for resourceId={}, targetCapacity={}",
			resource.getId(), targetCapacity);

		return TestActionResponse.inProgress("SCALING_UP", targetCapacity, request.getId());
	}

	/**
	 * 리소스가 존재할 경우에만 활성화를 수행합니다. (Safe Method)
	 *
	 * <p>
	 * 트랜잭션 롤백 방지를 위해 예외 대신 null을 반환합니다.
	 *
	 * @param surveyUuid Survey UUID
	 * @param user       요청 사용자
	 * @return TestActionResponse or null if resource not found
	 */
	@Transactional
	public TestActionResponse activateResourceIfPresent(UUID surveyUuid, User user) {
		log.info("Activating resource if present for surveyUuid={}", surveyUuid);

		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);

		return streamingResourceRepository.findBySurveyId(survey.getId())
			.map(resource -> {
				securityManager.validateWriteAccess(resource.getSurvey().getGame().getWorkspace(), user);
				return activateResourceInternal(resource);
			})
			.orElse(null);
	}

	/**
	 * 리소스 활성화 내부 로직 (트랜잭션 최적화).
	 */
	private TestActionResponse activateResourceInternal(StreamingResource resource) {
		// 이미 ACTIVE 상태면 현재 상태 반환
		if (resource.getStatus() == StreamingResourceStatus.ACTIVE) {
			return TestActionResponse.startTest("ACTIVE", resource.getCurrentCapacity());
		}

		// State Guard
		if (isInTransition(resource)) {
			throw new BusinessException(ErrorCode.RESOURCE_ALREADY_IN_TRANSITION);
		}

		int targetCapacity = resource.getMaxCapacity();

		// Phase 1: DB Update
		CapacityChangeRequest request = CapacityChangeRequest.create(
			resource, CapacityChangeType.ACTIVATE, targetCapacity);
		capacityChangeRequestRepository.save(request);

		resource.markScalingUp(targetCapacity);
		streamingResourceRepository.save(resource);

		// Phase 2: Async Execution
		executeAsyncCapacityChange(resource.getId(), request.getId(), targetCapacity, CapacityChangeType.ACTIVATE);

		return TestActionResponse.inProgress("SCALING_UP", targetCapacity, request.getId());
	}

	// ========== Helper Methods ==========

	/**
	 * Survey UUID로 리소스를 조회합니다.
	 */
	private StreamingResource findResourceBySurveyUuid(UUID surveyUuid, User user, boolean requireWrite) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);

		StreamingResource resource = streamingResourceRepository.findBySurveyId(survey.getId())
			.orElseThrow(StreamingResourceNotFoundException::new);

		if (requireWrite) {
			securityManager.validateWriteAccess(resource.getSurvey().getGame().getWorkspace(), user);
		} else {
			securityManager.validateReadAccess(resource.getSurvey().getGame().getWorkspace(), user);
		}

		return resource;
	}

	/**
	 * 리소스가 전환 중인지 확인하고 예외를 발생시킵니다.
	 */
	private void validateNotInTransition(StreamingResource resource) {
		if (isInTransition(resource)) {
			throw new BusinessException(ErrorCode.RESOURCE_ALREADY_IN_TRANSITION);
		}
	}

	/**
	 * 리소스가 전환 중인지 확인합니다.
	 */
	private boolean isInTransition(StreamingResource resource) {
		StreamingResourceStatus status = resource.getStatus();
		return status == StreamingResourceStatus.SCALING_UP ||
			status == StreamingResourceStatus.SCALING_DOWN ||
			status == StreamingResourceStatus.SCALING;
	}

	/**
	 * 비동기 용량 변경을 실행합니다 (Fail-Fast).
	 */
	private void executeAsyncCapacityChange(Long resourceId, Long requestId, int targetCapacity,
		CapacityChangeType type) {
		try {
			capacityChangeAsyncService.applyCapacityChange(resourceId, requestId, targetCapacity, type);
		} catch (org.springframework.core.task.TaskRejectedException e) {
			log.error("Async Task Queue Full! Rolling back transaction for resourceId={}", resourceId);
			throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
		}
	}
}
