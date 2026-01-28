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
		// 단, READY 상태에서도 AWS 상태가 ACTIVE인지 확인됨 (ERROR는 별도 handleErrorState에서 처리)
		if (desiredCapacity == 0) {
			log.debug("Resource in standby mode (desiredCapacity=0). AWS StreamGroup is ACTIVE. resourceId={}",
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
			StreamingResourceStatus previousStatus = resource.getStatus();
			resource.markError("AWS StreamGroup reported ERROR status");
			streamingResourceRepository.save(resource);
			log.error("Resource detected as ERROR via AWS sync. previousStatus={}, resourceId={}",
				previousStatus, resource.getId());
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
	 * <p>
	 * <b>비용 안전</b>: currentCapacity를 사용하여 의도한 용량만 복구합니다.
	 * maxCapacity를 사용하면 테스트 중(capacity=1)에도 전체 용량이 프로비저닝될 수 있습니다.
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

		// 현재 의도된 용량이 0이면 Self-Healing 불필요
		int targetCapacity = resource.getCurrentCapacity();
		if (targetCapacity <= 0) {
			log.debug("Self-healing skipped: currentCapacity is 0. resourceId={}", resourceId);
			return;
		}

		try {
			log.info("Triggering self-healing: Re-applying capacity configuration. resourceId={}, targetCapacity={}",
				resourceId, targetCapacity);

			// ⚠️ 비용 안전: maxCapacity가 아닌 currentCapacity 사용
			gameLiftService.updateStreamGroupCapacity(
				resource.getAwsStreamGroupId(),
				targetCapacity);

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
	 * <p>
	 * 순수하게 "변경 중인 상태"만 포함합니다.
	 * READY와 TESTING은 안정(Stable) 상태이므로 제외합니다.
	 *
	 * @param status 리소스 상태
	 * @return Transitional 상태이면 true
	 */
	public boolean isTransitionalState(StreamingResourceStatus status) {
		return status == StreamingResourceStatus.CREATING ||
			status == StreamingResourceStatus.PENDING ||
			status == StreamingResourceStatus.PROVISIONING ||
			status == StreamingResourceStatus.SCALING ||
			status == StreamingResourceStatus.SCALING_UP ||
			status == StreamingResourceStatus.SCALING_DOWN;
	}
}
