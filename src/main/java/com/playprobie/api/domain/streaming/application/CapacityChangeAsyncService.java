package com.playprobie.api.domain.streaming.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.streaming.dao.CapacityChangeRequestRepository;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.CapacityChangeRequest;
import com.playprobie.api.domain.streaming.domain.CapacityChangeType;
import com.playprobie.api.domain.streaming.domain.RequestStatus;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.infra.gamelift.GameLiftService;
import com.playprobie.api.infra.gamelift.exception.GameLiftResourceNotFoundException;
import com.playprobie.api.infra.gamelift.exception.GameLiftTransientException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 용량 변경 비동기 처리 서비스.
 *
 * <p>
 * StreamingResourceService와의 순환 참조를 방지하기 위해 분리됨.
 * REQUIRES_NEW 트랜잭션으로 격리하여 비동기 처리 결과를 독립적으로 커밋합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CapacityChangeAsyncService {

	private final StreamingResourceRepository resourceRepository;
	private final CapacityChangeRequestRepository requestRepository;
	private final GameLiftService gameLiftService;

	/**
	 * 비동기 용량 변경 처리.
	 *
	 * <p>
	 * 트랜잭션 경계를 최적화하여 AWS API 호출을 트랜잭션 외부로 분리합니다.
	 * DB 업데이트만 짧은 트랜잭션으로 처리하여 커넥션 고갈을 방지합니다.
	 */
	@Async("taskExecutor")
	public void applyCapacityChange(Long resourceId, Long requestId, int targetCapacity, CapacityChangeType type) {
		log.info("Async capacity change started: resourceId={}, requestId={}, target={}", resourceId, requestId,
			targetCapacity);

		StreamingResource resource = resourceRepository.findById(resourceId).orElseThrow();

		// Phase 1: DB 업데이트 (짧은 트랜잭션)
		updateRequestStatus(requestId, RequestStatus.PROCESSING, null);

		try {
			// Phase 2: AWS API 호출 (트랜잭션 외부)
			gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), targetCapacity);

			// Phase 3: 성공 처리 (독립 트랜잭션)
			updateResourceAndRequestOnSuccess(resourceId, requestId, type);
			log.info("Capacity change success: resourceId={}", resourceId);

		} catch (GameLiftTransientException e) {
			// Transient Error -> ERROR State (Manual Retry)
			log.warn("Capacity change transient failure: {}", e.getMessage());
			updateResourceAndRequestOnError(resourceId, requestId, "AWS 일시적 오류: " + e.getMessage(), false);

		} catch (GameLiftResourceNotFoundException e) {
			// Fatal Error -> FAILED_FATAL
			log.error("Capacity change fatal failure: {}", e.getMessage());
			updateResourceAndRequestOnError(resourceId, requestId, "CRITICAL: AWS 리소스 없음", true);

		} catch (Exception e) {
			// Unknown Error -> Failsafe Rollback attempted
			log.error("Capacity change unknown failure: {}", e.getMessage(), e);
			try {
				// Failsafe: Try to set capacity to 0 just in case
				gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), 0);
				updateResourceRollback(resourceId, requestId);
			} catch (Exception rollbackEx) {
				log.error("Failsafe rollback failed", rollbackEx);
				updateResourceAndRequestOnError(resourceId, requestId, "Failsafe failed: " + e.getMessage(), false);
			}
		}
	}

	/**
	 * 요청 상태를 업데이트합니다 (짧은 독립 트랜잭션).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateRequestStatus(Long requestId, RequestStatus status, String errorMessage) {
		CapacityChangeRequest request = requestRepository.findById(requestId).orElseThrow();

		if (status == RequestStatus.PROCESSING) {
			request.markProcessing();
		} else if (status == RequestStatus.COMPLETED) {
			request.markCompleted();
		} else if (status == RequestStatus.FAILED) {
			request.markFailed(errorMessage);
		} else if (status == RequestStatus.FAILED_FATAL) {
			request.markFailedFatal(errorMessage);
		}

		requestRepository.save(request);
	}

	/**
	 * 성공 시 리소스 및 요청 상태를 업데이트합니다 (독립 트랜잭션).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateResourceAndRequestOnSuccess(Long resourceId, Long requestId, CapacityChangeType type) {
		StreamingResource resource = resourceRepository.findById(resourceId).orElseThrow();
		CapacityChangeRequest request = requestRepository.findById(requestId).orElseThrow();

		if (type == CapacityChangeType.START_TEST) {
			resource.confirmStartTest();
		} else if (type == CapacityChangeType.ACTIVATE) {
			resource.markActive();
		} else {
			resource.confirmStopTest();
		}
		request.markCompleted();

		resourceRepository.save(resource);
		requestRepository.save(request);
	}

	/**
	 * 에러 발생 시 리소스 및 요청 상태를 업데이트합니다 (독립 트랜잭션).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateResourceAndRequestOnError(Long resourceId, Long requestId, String errorMessage, boolean isFatal) {
		StreamingResource resource = resourceRepository.findById(resourceId).orElseThrow();
		CapacityChangeRequest request = requestRepository.findById(requestId).orElseThrow();

		resource.markError(errorMessage);
		if (isFatal) {
			request.markFailedFatal("AWS Resource Not Found");
		} else {
			request.markFailed(errorMessage);
		}

		resourceRepository.save(resource);
		requestRepository.save(request);
	}

	/**
	 * Rollback 시 리소스 및 요청 상태를 업데이트합니다 (독립 트랜잭션).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateResourceRollback(Long resourceId, Long requestId) {
		StreamingResource resource = resourceRepository.findById(resourceId).orElseThrow();
		CapacityChangeRequest request = requestRepository.findById(requestId).orElseThrow();

		resource.rollbackScaling();
		request.markFailed("Rollback executed");

		resourceRepository.save(resource);
		requestRepository.save(request);
	}
}
