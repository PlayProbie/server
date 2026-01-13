package com.playprobie.api.domain.streaming.application;

import java.util.Optional;

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
 *
 * <p>
 * <b>Race Condition 처리</b>: 비동기 작업 중 리소스가 삭제된 경우 graceful하게 종료합니다.
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
	 *
	 * <p>
	 * <b>Race Condition</b>: 비동기 작업 중 리소스가 삭제된 경우 graceful하게 종료합니다.
	 */
	@Async("taskExecutor")
	public void applyCapacityChange(Long resourceId, Long requestId, int targetCapacity, CapacityChangeType type) {
		log.info("Async capacity change started: resourceId={}, requestId={}, target={}", resourceId, requestId,
			targetCapacity);

		// Race Condition 체크: 리소스가 이미 삭제되었으면 조기 종료
		Optional<StreamingResource> resourceOpt = resourceRepository.findById(resourceId);
		if (resourceOpt.isEmpty()) {
			log.warn("Resource already deleted during async processing. Skipping. resourceId={}", resourceId);
			return;
		}

		StreamingResource resource = resourceOpt.get();

		// Phase 1: DB 업데이트 (짧은 트랜잭션) - 요청이 삭제되었을 수 있음
		if (!updateRequestStatusSafely(requestId, RequestStatus.PROCESSING, null)) {
			log.warn("Request already deleted during async processing. Skipping. requestId={}", requestId);
			return;
		}

		try {
			// Phase 2: AWS API 호출 (트랜잭션 외부)
			gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), targetCapacity);

			// Phase 3: 성공 처리 (독립 트랜잭션) - 삭제된 경우 무시
			updateResourceAndRequestOnSuccessSafely(resourceId, requestId, type);
			log.info("Capacity change success: resourceId={}", resourceId);

		} catch (GameLiftTransientException e) {
			// Transient Error -> ERROR State (Manual Retry)
			log.warn("Capacity change transient failure: {}", e.getMessage());
			updateResourceAndRequestOnErrorSafely(resourceId, requestId, "AWS 일시적 오류: " + e.getMessage(), false);

		} catch (GameLiftResourceNotFoundException e) {
			// Fatal Error -> FAILED_FATAL
			log.error("Capacity change fatal failure: {}", e.getMessage());
			updateResourceAndRequestOnErrorSafely(resourceId, requestId, "CRITICAL: AWS 리소스 없음", true);

		} catch (Exception e) {
			// Unknown Error -> Failsafe Rollback attempted
			log.error("Capacity change unknown failure: {}", e.getMessage(), e);
			try {
				// Failsafe: Try to set capacity to 0 just in case
				gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), 0);
				updateResourceRollbackSafely(resourceId, requestId);
			} catch (Exception rollbackEx) {
				log.error("Failsafe rollback failed", rollbackEx);
				updateResourceAndRequestOnErrorSafely(resourceId, requestId, "Failsafe failed: " + e.getMessage(),
					false);
			}
		}
	}

	// ========== Safe Methods (Race Condition 처리) ==========

	/**
	 * 요청 상태를 안전하게 업데이트합니다.
	 *
	 * @return 성공 시 true, 요청이 존재하지 않으면 false
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean updateRequestStatusSafely(Long requestId, RequestStatus status, String errorMessage) {
		Optional<CapacityChangeRequest> requestOpt = requestRepository.findById(requestId);
		if (requestOpt.isEmpty()) {
			return false;
		}

		CapacityChangeRequest request = requestOpt.get();
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
		return true;
	}

	/**
	 * 성공 시 리소스 및 요청 상태를 안전하게 업데이트합니다 (독립 트랜잭션).
	 * <p>
	 * Race Condition: 리소스/요청이 삭제된 경우 무시합니다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateResourceAndRequestOnSuccessSafely(Long resourceId, Long requestId, CapacityChangeType type) {
		Optional<StreamingResource> resourceOpt = resourceRepository.findById(resourceId);
		Optional<CapacityChangeRequest> requestOpt = requestRepository.findById(requestId);

		if (resourceOpt.isEmpty() || requestOpt.isEmpty()) {
			log.warn("Resource or Request deleted during success update. Skipping. resourceId={}, requestId={}",
				resourceId, requestId);
			return;
		}

		StreamingResource resource = resourceOpt.get();
		CapacityChangeRequest request = requestOpt.get();

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
	 * 에러 발생 시 리소스 및 요청 상태를 안전하게 업데이트합니다 (독립 트랜잭션).
	 * <p>
	 * Race Condition: 리소스/요청이 삭제된 경우 무시합니다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateResourceAndRequestOnErrorSafely(Long resourceId, Long requestId, String errorMessage,
		boolean isFatal) {
		Optional<StreamingResource> resourceOpt = resourceRepository.findById(resourceId);
		Optional<CapacityChangeRequest> requestOpt = requestRepository.findById(requestId);

		if (resourceOpt.isEmpty() || requestOpt.isEmpty()) {
			log.warn("Resource or Request deleted during error update. Skipping. resourceId={}, requestId={}",
				resourceId, requestId);
			return;
		}

		StreamingResource resource = resourceOpt.get();
		CapacityChangeRequest request = requestOpt.get();

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
	 * Rollback 시 리소스 및 요청 상태를 안전하게 업데이트합니다 (독립 트랜잭션).
	 * <p>
	 * Race Condition: 리소스/요청이 삭제된 경우 무시합니다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateResourceRollbackSafely(Long resourceId, Long requestId) {
		Optional<StreamingResource> resourceOpt = resourceRepository.findById(resourceId);
		Optional<CapacityChangeRequest> requestOpt = requestRepository.findById(requestId);

		if (resourceOpt.isEmpty() || requestOpt.isEmpty()) {
			log.warn("Resource or Request deleted during rollback. Skipping. resourceId={}, requestId={}",
				resourceId, requestId);
			return;
		}

		StreamingResource resource = resourceOpt.get();
		CapacityChangeRequest request = requestOpt.get();

		resource.rollbackScaling();
		request.markFailed("Rollback executed");

		resourceRepository.save(resource);
		requestRepository.save(request);
	}

	// ========== Legacy Methods (하위 호환성 유지, 내부 호출 시 사용) ==========

	/**
	 * @deprecated Use {@link #updateRequestStatusSafely} instead
	 */
	@Deprecated
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateRequestStatus(Long requestId, RequestStatus status, String errorMessage) {
		updateRequestStatusSafely(requestId, status, errorMessage);
	}

	/**
	 * @deprecated Use {@link #updateResourceAndRequestOnSuccessSafely} instead
	 */
	@Deprecated
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateResourceAndRequestOnSuccess(Long resourceId, Long requestId, CapacityChangeType type) {
		updateResourceAndRequestOnSuccessSafely(resourceId, requestId, type);
	}

	/**
	 * @deprecated Use {@link #updateResourceAndRequestOnErrorSafely} instead
	 */
	@Deprecated
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateResourceAndRequestOnError(Long resourceId, Long requestId, String errorMessage, boolean isFatal) {
		updateResourceAndRequestOnErrorSafely(resourceId, requestId, errorMessage, isFatal);
	}

	/**
	 * @deprecated Use {@link #updateResourceRollbackSafely} instead
	 */
	@Deprecated
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateResourceRollback(Long resourceId, Long requestId) {
		updateResourceRollbackSafely(resourceId, requestId);
	}
}
