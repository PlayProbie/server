package com.playprobie.api.domain.streaming.application;

import java.util.Optional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.global.config.properties.AwsProperties;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;
import com.playprobie.api.infra.gamelift.GameLiftService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.gameliftstreams.model.CreateApplicationResponse;
import software.amazon.awssdk.services.gameliftstreams.model.CreateStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.GetStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.StreamGroupStatus;

/**
 * 스트리밍 리소스 프로비저닝 담당 서비스 (Async).
 *
 * <p>
 * 비동기 실행을 위해 메인 서비스에서 분리되었습니다.
 * <b>트랜잭션 최적화</b>: DB 업데이트만 짧은 트랜잭션으로 처리하여 커넥션 고갈을 방지합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingProvisioner {

	private static final String SUFFIX_APP = "-app";
	private static final String SUFFIX_GROUP = "-group";

	private final StreamingResourceRepository streamingResourceRepository;
	private final GameLiftService gameLiftService;
	private final AwsProperties awsProperties;

	/**
	 * 비동기로 AWS 리소스를 생성하고 연결합니다.
	 *
	 * <p>
	 * <b>트랜잭션 분리</b>: AWS API 호출은 트랜잭션 외부에서 실행되며,
	 * DB 업데이트만 개별 짧은 트랜잭션으로 처리합니다.
	 *
	 * @param resourceId StreamingResource PK
	 */
	@Async("taskExecutor")
	public void provisionResourceAsync(Long resourceId) {
		log.info("Starting async provisioning for resourceId={}", resourceId);

		// AWS 리소스 추적 (Rollback용)
		String createdAppArn = null;
		String createdGroupArn = null;

		try {
			// 0. 리소스 조회 (Fetch Join으로 연관 엔티티 즉시 로딩 - LazyInitializationException 방지)
			Optional<StreamingResource> resourceOpt = streamingResourceRepository.findByIdWithAssociations(resourceId);
			if (resourceOpt.isEmpty()) {
				log.warn("Resource already deleted before provisioning. resourceId={}", resourceId);
				return;
			}
			StreamingResource resource = resourceOpt.get();

			// 1. AWS Application 생성
			String s3Uri = String.format("s3://%s/%s", awsProperties.s3().bucketName(),
				resource.getBuild().getS3Prefix());

			log.info("Creating AWS Application for resourceId={}", resourceId);
			CreateApplicationResponse appResponse = gameLiftService.createApplication(
				resource.getSurvey().getUuid() + SUFFIX_APP,
				s3Uri,
				resource.getBuild().getExecutablePath(),
				resource.getBuild().getOsType());

			createdAppArn = appResponse.arn();

			// DB 업데이트: PROVISIONING 상태 (삭제된 경우 조기 종료)
			if (!saveProvisioningState(resourceId, createdAppArn)) {
				log.warn("Resource deleted during provisioning. Cleaning up. resourceId={}", resourceId);
				cleanupAwsResourceOnFailure(createdAppArn, null, resourceId);
				return;
			}

			// 2. AWS StreamGroup 생성
			log.info("Creating AWS StreamGroup for resourceId={}", resourceId);
			CreateStreamGroupResponse groupResponse = gameLiftService.createStreamGroup(
				resource.getSurvey().getUuid() + SUFFIX_GROUP,
				resource.getInstanceType());

			createdGroupArn = groupResponse.arn();

			// 3. Application을 StreamGroup에 연결 (상태 안정화 대기)
			waitForStreamGroupStable(groupResponse.arn());
			gameLiftService.associateApplication(groupResponse.arn(), appResponse.arn());

			// DB 업데이트: READY 상태
			saveCompletedState(resourceId, groupResponse.arn());

			log.info("Async provisioning completed: resourceId={}, appArn={}, groupArn={}",
				resourceId, appResponse.arn(), groupResponse.arn());

		} catch (Exception e) {
			log.error("Async provisioning failed for resourceId={}", resourceId, e);

			// AWS 리소스 Rollback (고아 리소스 방지)
			cleanupAwsResourceOnFailure(createdAppArn, createdGroupArn, resourceId);

			// DB 상태 업데이트: ERROR
			saveErrorState(resourceId, e.getMessage());
		}
	}

	// ========== AWS Cleanup ==========

	/**
	 * 프로비저닝 실패 시 AWS 리소스를 정리합니다.
	 *
	 * <p>
	 * <b>고아 리소스 방지</b>: 삭제 실패 시 ERROR 로그를 남겨 수동 조치가 가능하도록 합니다.
	 */
	private void cleanupAwsResourceOnFailure(String appArn, String groupArn, Long resourceId) {
		// StreamGroup 삭제 시도
		if (groupArn != null) {
			try {
				gameLiftService.deleteStreamGroup(groupArn);
				log.info("Rollback: Deleted orphan StreamGroup groupArn={}", groupArn);
			} catch (Exception e) {
				log.error("[ORPHAN_RESOURCE] Failed to delete StreamGroup. Manual cleanup required. " +
					"resourceId={}, groupArn={}, error={}", resourceId, groupArn, e.getMessage());
			}
		}

		// Application 삭제 시도
		if (appArn != null) {
			try {
				gameLiftService.deleteApplication(appArn);
				log.info("Rollback: Deleted orphan Application appArn={}", appArn);
			} catch (Exception e) {
				log.error("[ORPHAN_RESOURCE] Failed to delete Application. Manual cleanup required. " +
					"resourceId={}, appArn={}, error={}", resourceId, appArn, e.getMessage());
			}
		}
	}

	// ========== DB State Updates (Short Transactions) ==========

	/**
	 * Application 생성 완료 상태를 저장합니다 (PROVISIONING).
	 *
	 * @return 성공 시 true, 리소스가 삭제된 경우 false
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean saveProvisioningState(Long resourceId, String appArn) {
		Optional<StreamingResource> resourceOpt = streamingResourceRepository.findById(resourceId);
		if (resourceOpt.isEmpty()) {
			log.warn("Resource deleted during provisioning state save. resourceId={}", resourceId);
			return false;
		}

		StreamingResource resource = resourceOpt.get();
		resource.assignApplication(appArn);
		streamingResourceRepository.save(resource);
		log.debug("Saved provisioning state: resourceId={}, appArn={}", resourceId, appArn);
		return true;
	}

	/**
	 * 프로비저닝 완료 상태를 저장합니다 (READY).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void saveCompletedState(Long resourceId, String groupArn) {
		Optional<StreamingResource> resourceOpt = streamingResourceRepository.findById(resourceId);
		if (resourceOpt.isEmpty()) {
			log.warn("Resource deleted during completed state save. resourceId={}", resourceId);
			return;
		}

		StreamingResource resource = resourceOpt.get();
		resource.assignStreamGroup(groupArn);
		streamingResourceRepository.save(resource);
		log.debug("Saved completed state: resourceId={}, groupArn={}", resourceId, groupArn);
	}

	/**
	 * 에러 상태를 저장합니다 (ERROR).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void saveErrorState(Long resourceId, String errorMessage) {
		Optional<StreamingResource> resourceOpt = streamingResourceRepository.findById(resourceId);
		if (resourceOpt.isEmpty()) {
			log.warn("Resource deleted during error state save. resourceId={}", resourceId);
			return;
		}

		StreamingResource resource = resourceOpt.get();
		resource.markError(errorMessage);
		streamingResourceRepository.save(resource);
		log.debug("Saved error state: resourceId={}, error={}", resourceId, errorMessage);
	}

	// ========== Polling ==========

	/**
	 * StreamGroup이 안정적인 상태(ACTIVATING이 아님)가 될 때까지 대기합니다.
	 *
	 * <p>
	 * Polling 설정은 application.yml의 aws.gamelift 섹션에서 조절 가능합니다:
	 * - polling-interval: 폴링 간격 (기본값: 5초)
	 * - max-polling-attempts: 최대 시도 횟수 (기본값: 30회)
	 *
	 * @throws BusinessException 타임아웃 시 AWS_PROVISIONING_TIMEOUT 예외 발생
	 */
	private void waitForStreamGroupStable(String streamGroupId) {
		long pollingMillis = awsProperties.gamelift().pollingInterval().toMillis();
		int maxAttempts = awsProperties.gamelift().maxPollingAttempts();

		for (int i = 0; i < maxAttempts; i++) {
			GetStreamGroupResponse response = gameLiftService.getStreamGroupStatus(streamGroupId);
			StreamGroupStatus status = response.status();
			log.info("Waiting for StreamGroup stable: status={}, attempt={}/{}", status, i + 1, maxAttempts);

			if (status != StreamGroupStatus.ACTIVATING) {
				log.info("StreamGroup is now stable: status={}", status);
				return;
			}

			try {
				Thread.sleep(pollingMillis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
			}
		}

		// Timeout 시 명시적 예외 발생
		log.error("StreamGroup stability wait timed out after {} attempts: {}", maxAttempts, streamGroupId);
		throw new BusinessException(ErrorCode.GAMELIFT_PROVISIONING_TIMEOUT);
	}
}
