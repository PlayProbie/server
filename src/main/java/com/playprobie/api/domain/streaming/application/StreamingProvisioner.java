package com.playprobie.api.domain.streaming.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;
import com.playprobie.api.infra.config.AwsProperties;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingProvisioner {

	private final StreamingResourceRepository streamingResourceRepository;
	private final GameLiftService gameLiftService;
	private final AwsProperties awsProperties;

	/**
	 * 비동기로 AWS 리소스를 생성하고 연결합니다.
	 *
	 * @param resourceId StreamingResource PK
	 */
	@Async("taskExecutor")
	@Transactional
	public void provisionResourceAsync(Long resourceId) {
		log.info("Starting async provisioning for resourceId={}", resourceId);

		StreamingResource resource = streamingResourceRepository.findById(resourceId)
			.orElseThrow(() -> new BusinessException(ErrorCode.STREAMING_RESOURCE_NOT_FOUND));

		try {
			// 1. AWS Application 생성
			String s3Uri = String.format("s3://%s/%s", awsProperties.getS3().getBucketName(),
				resource.getBuild().getS3Prefix());

			log.info("Creating AWS Application for resourceId={}", resourceId);
			CreateApplicationResponse appResponse = gameLiftService.createApplication(
				resource.getSurvey().getName() + "-app",
				s3Uri,
				resource.getBuild().getExecutablePath(),
				resource.getBuild().getOsType());

			resource.assignApplication(appResponse.arn());
			streamingResourceRepository.save(resource); // PROVISIONING 상태 저장

			// 2. AWS StreamGroup 생성
			log.info("Creating AWS StreamGroup for resourceId={}", resourceId);
			CreateStreamGroupResponse groupResponse = gameLiftService.createStreamGroup(
				resource.getSurvey().getName() + "-group",
				resource.getInstanceType());

			// 3. Application을 StreamGroup에 연결 (상태 안정화 대기)
			waitForStreamGroupStable(groupResponse.arn());
			gameLiftService.associateApplication(groupResponse.arn(), appResponse.arn());

			resource.assignStreamGroup(groupResponse.arn());
			streamingResourceRepository.save(resource); // READY 상태 저장

			log.info("Async provisioning completed: resourceId={}, appArn={}, groupArn={}",
				resource.getId(), appResponse.arn(), groupResponse.arn());

		} catch (Exception e) {
			log.error("Async provisioning failed for resourceId={}", resourceId, e);
			resource.markError(e.getMessage());
			streamingResourceRepository.save(resource);
		}
	}

	/**
	 * StreamGroup이 안정적인 상태(ACTIVATING이 아님)가 될 때까지 대기합니다.
	 */
	private void waitForStreamGroupStable(String streamGroupId) {
		int maxAttempts = 60;
		for (int i = 0; i < maxAttempts; i++) {
			GetStreamGroupResponse response = gameLiftService.getStreamGroupStatus(streamGroupId);
			StreamGroupStatus status = response.status();
			log.info("Waiting for StreamGroup stable: status={}, attempt={}", status, i + 1);

			if (status != StreamGroupStatus.ACTIVATING) {
				return;
			}

			try {
				Thread.sleep(1000); // 1초 대기
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
			}
		}
		log.warn("StreamGroup stability wait timed out: {}", streamGroupId);
	}
}
