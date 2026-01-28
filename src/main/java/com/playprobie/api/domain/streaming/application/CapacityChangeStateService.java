package com.playprobie.api.domain.streaming.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.streaming.dao.CapacityChangeRequestRepository;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.CapacityChangeRequest;
import com.playprobie.api.domain.streaming.domain.CapacityChangeType;
import com.playprobie.api.domain.streaming.domain.RequestStatus;
import com.playprobie.api.domain.streaming.domain.StreamingResource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 용량 변경 요청 상태 관리 서비스.
 *
 * <p>
 * <b>Self-Invocation 문제 해결</b>: CapacityChangeAsyncService에서 분리되어
 * {@code @Transactional(REQUIRES_NEW)} 프록시가 정상 동작하도록 합니다.
 *
 * <p>
 * 각 메서드는 독립된 트랜잭션에서 실행되어 비동기 처리 결과를 즉시 커밋하고,
 * Race Condition 발생 시 graceful하게 처리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CapacityChangeStateService {

	private final StreamingResourceRepository resourceRepository;
	private final CapacityChangeRequestRepository requestRepository;

	/**
	 * 요청 상태를 안전하게 업데이트합니다.
	 *
	 * @param requestId    요청 ID
	 * @param status       변경할 상태
	 * @param errorMessage 에러 메시지 (FAILED/FAILED_FATAL 상태에서 사용)
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
		} else if (type == CapacityChangeType.STOP_TEST) {
			resource.confirmStopTest();
		} else {
			// 새로운 CapacityChangeType 추가 시 명시적 처리 필요
			throw new IllegalStateException("Unknown CapacityChangeType: " + type);
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

	/**
	 * 요청의 재시도 횟수를 증가시킵니다 (독립 트랜잭션).
	 *
	 * @param requestId 요청 ID
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void incrementRetryCount(Long requestId) {
		Optional<CapacityChangeRequest> requestOpt = requestRepository.findById(requestId);
		if (requestOpt.isEmpty()) {
			log.warn("Request deleted during retry count increment. Skipping. requestId={}", requestId);
			return;
		}

		CapacityChangeRequest request = requestOpt.get();
		request.incrementRetryCount();
		requestRepository.save(request);
		log.debug("Incremented retry count for requestId={}, newCount={}", requestId, request.getRetryCount());
	}

	/**
	 * 최대 재시도 횟수 초과로 인해 요청을 FAILED 상태로 변경합니다 (독립 트랜잭션).
	 *
	 * @param requestId 요청 ID
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markRequestFailedDueToMaxRetries(Long requestId) {
		Optional<CapacityChangeRequest> requestOpt = requestRepository.findByIdWithResource(requestId);
		if (requestOpt.isEmpty()) {
			log.warn("Request deleted during max retry failure marking. Skipping. requestId={}", requestId);
			return;
		}

		CapacityChangeRequest request = requestOpt.get();
		StreamingResource resource = request.getResource();

		request.markFailed("Maximum retry count exceeded (" + CapacityChangeRequest.getMaxRetryCount() + ")");
		resource.markError("Capacity change failed: Maximum retry count exceeded");

		requestRepository.save(request);
		resourceRepository.save(resource);
		log.warn("Marked request as FAILED due to max retries. requestId={}, resourceId={}",
			requestId, resource.getId());
	}
}
