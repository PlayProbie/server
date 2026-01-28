package com.playprobie.api.domain.streaming.application;

import java.util.Optional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
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
 *
 * <p>
 * <b>Self-Invocation 해결</b>: 상태 업데이트 메서드를 {@link CapacityChangeStateService}로 분리하여
 * {@code @Transactional(REQUIRES_NEW)} 프록시가 정상 동작하도록 합니다.
 *
 * <p>
 * <b>Race Condition 처리</b>: 비동기 작업 중 리소스가 삭제된 경우 graceful하게 종료합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CapacityChangeAsyncService {

	private final StreamingResourceRepository resourceRepository;
	private final CapacityChangeStateService capacityChangeStateService;
	private final GameLiftService gameLiftService;

	/**
	 * 비동기 용량 변경 처리.
	 *
	 * <p>
	 * 트랜잭션 경계를 최적화하여 AWS API 호출을 트랜잭션 외부로 분리합니다.
	 * DB 업데이트는 {@link CapacityChangeStateService}를 통해 독립된 짧은 트랜잭션으로 처리하여 커넥션 고갈을 방지합니다.
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

		// Phase 1: DB 업데이트 (짧은 트랜잭션 - 별도 서비스 호출로 프록시 적용) - 요청이 삭제되었을 수 있음
		if (!capacityChangeStateService.updateRequestStatusSafely(requestId, RequestStatus.PROCESSING, null)) {
			log.warn("Request already deleted during async processing. Skipping. requestId={}", requestId);
			return;
		}

		try {
			// Phase 2: AWS API 호출 (트랜잭션 외부)
			gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), targetCapacity);

			// Phase 3: 성공 처리 (독립 트랜잭션 - 별도 서비스 호출) - 삭제된 경우 무시
			capacityChangeStateService.updateResourceAndRequestOnSuccessSafely(resourceId, requestId, type);
			log.info("Capacity change success: resourceId={}", resourceId);

		} catch (GameLiftTransientException e) {
			// Transient Error -> ERROR State (Manual Retry)
			log.warn("Capacity change transient failure: {}", e.getMessage());
			capacityChangeStateService.updateResourceAndRequestOnErrorSafely(
				resourceId, requestId, "AWS 일시적 오류: " + e.getMessage(), false);

		} catch (GameLiftResourceNotFoundException e) {
			// Fatal Error -> FAILED_FATAL
			log.error("Capacity change fatal failure: {}", e.getMessage());
			capacityChangeStateService.updateResourceAndRequestOnErrorSafely(
				resourceId, requestId, "CRITICAL: AWS 리소스 없음", true);

		} catch (Exception e) {
			// Unknown Error -> Failsafe Rollback attempted
			log.error("Capacity change unknown failure: {}", e.getMessage(), e);
			try {
				// Failsafe: Try to set capacity to 0 just in case
				gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), 0);
				capacityChangeStateService.updateResourceRollbackSafely(resourceId, requestId);
			} catch (Exception rollbackEx) {
				// 🚨 CRITICAL: Rollback 실패 - AWS 인스턴스가 계속 실행될 수 있음
				log.error("[AWS_COST_RISK] Failsafe rollback FAILED! AWS instances may still be running. " +
					"Manual intervention required. resourceId={}, targetCapacity={}, error={}",
					resourceId, targetCapacity, rollbackEx.getMessage(), rollbackEx);

				// FAILED_FATAL 상태로 마킹하여 수동 개입 유도
				capacityChangeStateService.updateResourceAndRequestOnErrorSafely(
					resourceId, requestId,
					"[MANUAL_INTERVENTION_REQUIRED] Failsafe rollback failed. AWS instances may be running. Original error: "
						+ e.getMessage(),
					true); // isFatal = true
			}
		}
	}
}
