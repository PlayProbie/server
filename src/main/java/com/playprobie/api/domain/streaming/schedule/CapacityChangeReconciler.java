package com.playprobie.api.domain.streaming.schedule;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.playprobie.api.domain.streaming.application.CapacityChangeAsyncService;
import com.playprobie.api.domain.streaming.dao.CapacityChangeRequestRepository;
import com.playprobie.api.domain.streaming.domain.CapacityChangeRequest;
import com.playprobie.api.domain.streaming.domain.RequestStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Capacity Change Reconciler.
 *
 * <p>
 * 서버 장애, 재시작, 혹은 비동기 큐 유실 등으로 인해 PENDING 상태로 멈춰있는
 * 용량 변경 요청을 주기적으로 찾아 재처리합니다.
 * ShedLock을 사용하여 분산 환경에서 중복 실행을 방지합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CapacityChangeReconciler {

	private final CapacityChangeRequestRepository requestRepository;
	private final CapacityChangeAsyncService asyncService;

	// 1분마다 실행, 최소 락 유지시간 30초, 서버 시작 후 10초 대기 (테이블 생성 보장)
	@Scheduled(fixedDelay = 60000, initialDelay = 10000)
	@SchedulerLock(name = "CapacityChangeReconciler_reconcile", lockAtLeastFor = "PT30S", lockAtMostFor = "PT50S")
	public void reconcile() {
		// 1. PENDING 상태로 1분 이상 지난 요청 조회 (Stuck Requests)
		LocalDateTime threshold = LocalDateTime.now().minusMinutes(1);
		List<CapacityChangeRequest> stuckRequests = requestRepository
			.findAllByStatusAndCreatedAtBefore(RequestStatus.PENDING, threshold);

		if (!stuckRequests.isEmpty()) {
			log.info("Found {} stuck capacity change requests. Resubmitting...", stuckRequests.size());
		}

		// 2. 재처리 수행
		for (CapacityChangeRequest req : stuckRequests) {
			try {
				log.info("Reconciling request: id={}", req.getId());
				asyncService.applyCapacityChange(
					req.getResource().getId(),
					req.getId(),
					req.getTargetCapacity(),
					req.getType());
			} catch (Exception e) {
				log.error("Failed to reconcile request id={}", req.getId(), e);
				// 반복 실패 방지를 위해 별도 카운트나 FAILED 처리 로직이 필요할 수 있으나,
				// 현재는 AsyncService 내에서 예외 시 FAILED/ERROR 처리하므로 맡김.
			}
		}
	}
}
