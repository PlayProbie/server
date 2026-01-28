package com.playprobie.api.domain.streaming.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.StreamingResource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 스트리밍 리소스 상태 관리 서비스.
 *
 * <p>
 * <b>Self-Invocation 문제 해결</b>: StreamingProvisioner에서 분리되어
 * {@code @Transactional(REQUIRES_NEW)} 프록시가 정상 동작하도록 합니다.
 *
 * <p>
 * 각 메서드는 독립된 트랜잭션에서 실행되어 커넥션 고갈을 방지하고,
 * 비동기 작업 중 에러 발생 시 상태 불일치를 방지합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingStateService {

	private final StreamingResourceRepository streamingResourceRepository;

	/**
	 * Application 생성 완료 상태를 저장합니다 (PROVISIONING).
	 *
	 * @param resourceId 리소스 ID
	 * @param appArn     AWS Application ARN
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
	 *
	 * @param resourceId 리소스 ID
	 * @param groupArn   AWS StreamGroup ARN
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
	 *
	 * @param resourceId   리소스 ID
	 * @param errorMessage 에러 메시지
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
}
