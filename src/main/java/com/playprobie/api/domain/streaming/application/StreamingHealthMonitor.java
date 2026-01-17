package com.playprobie.api.domain.streaming.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.streaming.dao.SelfHealingLogRepository;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.SelfHealingLog;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.domain.streaming.domain.StreamingResourceStatus;
import com.playprobie.api.infra.gamelift.GameLiftService;
import com.playprobie.api.infra.gamelift.exception.GameLiftQuotaExceededException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.gameliftstreams.model.GetStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.StreamGroupStatus;

/**
 * AWS 상태 동기화 및 Self-Healing 담당 서비스.
 *
 * <p>
 * DB 기반 쿨다운을 사용하여 Scale-Out 환경을 지원합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StreamingHealthMonitor {

	private static final int SELF_HEALING_COOLDOWN_SECONDS = 60;

	private final StreamingResourceRepository streamingResourceRepository;
	private final SelfHealingLogRepository selfHealingLogRepository;
	private final GameLiftService gameLiftService;

	/**
	 * AWS 상태를 동기화하고 필요 시 Self-Healing을 수행합니다.
	 *
	 * @param resource 대상 리소스
	 * @return AWS 응답 (상태 동기화용)
	 */
	@Transactional
	public GetStreamGroupResponse synchronizeAndHeal(StreamingResource resource) {
		if (resource.getAwsStreamGroupId() == null) {
			return null;
		}

		try {
			GetStreamGroupResponse awsResponse = gameLiftService.getStreamGroupStatus(
				resource.getAwsStreamGroupId());

			synchronizeState(resource, awsResponse);

			return awsResponse;
		} catch (Exception e) {
			log.warn("Failed to sync status with AWS for resourceId={}: {}",
				resource.getId(), e.getMessage());
			return null;
		}
	}

	/**
	 * AWS 상태와 DB 상태를 동기화합니다.
	 *
	 * @param resource    대상 리소스
	 * @param awsResponse AWS 응답
	 */
	@Transactional
	public void synchronizeState(StreamingResource resource, GetStreamGroupResponse awsResponse) {
		StreamGroupStatus awsStatus = awsResponse.status();

		if (awsStatus == StreamGroupStatus.ACTIVE) {
			handleActiveState(resource, awsResponse);
		} else if (awsStatus == StreamGroupStatus.ERROR) {
			handleErrorState(resource);
		}
	}

	private void handleActiveState(StreamingResource resource, GetStreamGroupResponse awsResponse) {
		int desiredCapacity = resource.getMaxCapacity();
		int allocatedCapacity = extractAllocatedCapacity(awsResponse);

		if (allocatedCapacity >= desiredCapacity) {
			// 용량 확보됨 → DB 상태를 ACTIVE로 동기화
			if (resource.getStatus() != StreamingResourceStatus.ACTIVE) {
				resource.markActive();
				streamingResourceRepository.save(resource);
				log.info("Resource verified as ACTIVE and READY (Capacity {}/{}) via sync logic. resourceId={}",
					allocatedCapacity, desiredCapacity, resource.getId());
			}
		} else {
			// 용량 부족 → Self-Healing
			log.warn("Capacity Mismatch detected! AWS Active but Allocated: {}, Desired: {}. resourceId={}",
				allocatedCapacity, desiredCapacity, resource.getId());

			triggerSelfHealing(resource);
		}
	}

	private void handleErrorState(StreamingResource resource) {
		if (resource.getStatus() != StreamingResourceStatus.ERROR) {
			resource.markError("AWS StreamGroup reported ERROR status");
			streamingResourceRepository.save(resource);
			log.error("Resource verified as ERROR via sync logic. resourceId={}", resource.getId());
		}
	}

	private int extractAllocatedCapacity(GetStreamGroupResponse awsResponse) {
		if (awsResponse.locationStates() == null || awsResponse.locationStates().isEmpty()) {
			return 0;
		}
		return awsResponse.locationStates().get(0).allocatedCapacity();
	}

	/**
	 * 용량 설정을 재요청합니다 (DB 기반 쿨다운 적용).
	 *
	 * @param resource 대상 리소스
	 */
	@Transactional
	public void triggerSelfHealing(StreamingResource resource) {
		Long resourceId = resource.getId();

		// DB 기반 쿨다운 체크
		if (isCooldownActive(resourceId)) {
			log.info("Self-healing skipped due to cooldown. resourceId={}", resourceId);
			return;
		}

		try {
			log.info("Triggering self-healing: Re-applying capacity configuration. resourceId={}",
				resourceId);

			gameLiftService.updateStreamGroupCapacity(
				resource.getAwsStreamGroupId(),
				resource.getMaxCapacity());

			// 쿨다운 기록 갱신
			recordSelfHealingAttempt(resourceId);

		} catch (GameLiftQuotaExceededException e) {
			log.error("Self-healing SKIPPED due to Quota Limit for resourceId={}: {}",
				resourceId, e.getMessage());
		} catch (Exception e) {
			log.error("Self-healing failed for resourceId={}: {}", resourceId, e.getMessage());
		}
	}

	/**
	 * 쿨다운이 활성 상태인지 확인합니다 (DB 기반).
	 *
	 * @param resourceId 리소스 ID
	 * @return 쿨다운 중이면 true
	 */
	private boolean isCooldownActive(Long resourceId) {
		return selfHealingLogRepository.findByResourceId(resourceId)
			.map(log -> log.isCooldownActive(SELF_HEALING_COOLDOWN_SECONDS))
			.orElse(false);
	}

	/**
	 * Self-Healing 시도를 기록합니다.
	 *
	 * @param resourceId 리소스 ID
	 */
	private void recordSelfHealingAttempt(Long resourceId) {
		SelfHealingLog healingLog = selfHealingLogRepository.findByResourceId(resourceId)
			.orElseGet(() -> SelfHealingLog.builder().resourceId(resourceId).build());

		healingLog.recordAttempt();
		selfHealingLogRepository.save(healingLog);
	}

	/**
	 * Transitional State 여부를 확인합니다.
	 *
	 * @param status 리소스 상태
	 * @return Transitional 상태이면 true
	 */
	public boolean isTransitionalState(StreamingResourceStatus status) {
		return status == StreamingResourceStatus.CREATING ||
			status == StreamingResourceStatus.PENDING ||
			status == StreamingResourceStatus.PROVISIONING ||
			status == StreamingResourceStatus.READY ||
			status == StreamingResourceStatus.TESTING ||
			status == StreamingResourceStatus.SCALING;
	}
}
