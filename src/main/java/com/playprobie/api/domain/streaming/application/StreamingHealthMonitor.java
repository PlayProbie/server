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
		// currentCapacity를 사용: READY 상태에서는 0, ACTIVE 상태에서는 설정된 값
		int desiredCapacity = resource.getCurrentCapacity();
		int awsConfiguredCapacity = extractAlwaysOnCapacity(awsResponse);
		int allocatedCapacity = extractAllocatedCapacity(awsResponse);

		// desiredCapacity가 0이면 Self-healing 불필요 (READY 상태)
		if (desiredCapacity == 0) {
			log.debug("Resource in standby mode (desiredCapacity=0). Skipping health check. resourceId={}",
				resource.getId());
			return;
		}

		// 1. 설정값 비교: AWS alwaysOnCapacity vs DB desiredCapacity
		if (awsConfiguredCapacity >= desiredCapacity) {
			// 설정값 정상 → 인스턴스 할당 상태에 따라 DB 동기화
			if (allocatedCapacity >= desiredCapacity) {
				// 인스턴스도 할당됨 → ACTIVE 상태로 마킹
				if (resource.getStatus() != StreamingResourceStatus.ACTIVE) {
					resource.markActive();
					streamingResourceRepository.save(resource);
					log.info("Resource verified as ACTIVE and READY (Capacity {}/{}) via sync logic. resourceId={}",
						allocatedCapacity, desiredCapacity, resource.getId());
				}
			} else {
				// 설정은 맞지만 인스턴스 할당 중 → 정상 (AWS 시간 필요)
				log.debug(
					"AWS provisioning instances. Config OK (alwaysOn={}), waiting for allocation ({}/{}). resourceId={}",
					awsConfiguredCapacity, allocatedCapacity, desiredCapacity, resource.getId());
			}
		} else {
			// 설정값 불일치 → Self-Healing 필요
			log.warn("Capacity Config Mismatch! AWS alwaysOn: {}, Desired: {}. resourceId={}",
				awsConfiguredCapacity, desiredCapacity, resource.getId());
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
	 * AWS 응답에서 alwaysOnCapacity 설정값을 추출합니다.
	 *
	 * @param awsResponse AWS 응답
	 * @return alwaysOnCapacity 설정값
	 */
	private int extractAlwaysOnCapacity(GetStreamGroupResponse awsResponse) {
		if (awsResponse.locationStates() == null || awsResponse.locationStates().isEmpty()) {
			return 0;
		}
		return awsResponse.locationStates().get(0).alwaysOnCapacity();
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
