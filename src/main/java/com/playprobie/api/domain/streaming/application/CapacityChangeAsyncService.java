package com.playprobie.api.domain.streaming.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.streaming.dao.CapacityChangeRequestRepository;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.CapacityChangeRequest;
import com.playprobie.api.domain.streaming.domain.CapacityChangeType;
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

	@Async("taskExecutor")
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void applyCapacityChange(Long resourceId, Long requestId, int targetCapacity, CapacityChangeType type) {
		log.info("Async capacity change started: resourceId={}, requestId={}, target={}", resourceId, requestId,
			targetCapacity);

		StreamingResource resource = resourceRepository.findById(resourceId).orElseThrow();
		CapacityChangeRequest request = requestRepository.findById(requestId).orElseThrow();

		try {
			request.markProcessing();
			requestRepository.save(request);

			// AWS API Call (Retries handled inside)
			gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), targetCapacity);

			// Success: Confirm State
			if (type == CapacityChangeType.START_TEST) {
				resource.confirmStartTest();
			} else if (type == CapacityChangeType.ACTIVATE) {
				resource.markActive();
			} else {
				resource.confirmStopTest();
			}
			request.markCompleted();

			log.info("Capacity change success: resourceId={}", resourceId);

		} catch (GameLiftTransientException e) {
			// Transient Error -> ERROR State (Manual Retry)
			log.warn("Capacity change transient failure: {}", e.getMessage());
			resource.markError("AWS 일시적 오류: " + e.getMessage());
			request.markFailed(e.getMessage());

		} catch (GameLiftResourceNotFoundException e) {
			// Fatal Error -> FAILED_FATAL
			log.error("Capacity change fatal failure: {}", e.getMessage());
			resource.markError("CRITICAL: AWS 리소스 없음");
			request.markFailedFatal("AWS Resource Not Found");

		} catch (Exception e) {
			// Unknown Error -> Failsafe Rollback attempted
			log.error("Capacity change unknown failure: {}", e.getMessage(), e);
			try {
				// Failsafe: Try to set capacity to 0 just in case
				gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), 0);
				resource.rollbackScaling();
			} catch (Exception rollbackEx) {
				log.error("Failsafe rollback failed", rollbackEx);
				resource.markError("Failsafe failed: " + e.getMessage());
			}
			request.markFailed(e.getMessage());
		}

		resourceRepository.save(resource);
		requestRepository.save(request);
	}
}
