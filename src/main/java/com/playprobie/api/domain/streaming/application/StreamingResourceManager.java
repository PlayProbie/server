package com.playprobie.api.domain.streaming.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.playprobie.api.domain.game.dao.GameBuildRepository;
import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.game.exception.GameBuildNotFoundException;
import com.playprobie.api.domain.model.StreamClass;
import com.playprobie.api.domain.streaming.dao.CapacityChangeRequestRepository;
import com.playprobie.api.domain.streaming.dao.SelfHealingLogRepository;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.domain.streaming.domain.StreamingResourceStatus;
import com.playprobie.api.domain.streaming.dto.CreateStreamingResourceRequest;
import com.playprobie.api.domain.streaming.dto.ResourceStatusResponse;
import com.playprobie.api.domain.streaming.dto.StreamingResourceResponse;
import com.playprobie.api.domain.streaming.exception.StreamClassIncompatibleException;
import com.playprobie.api.domain.streaming.exception.StreamingResourceAlreadyExistsException;
import com.playprobie.api.domain.streaming.exception.StreamingResourceNotFoundException;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.exception.SurveyNotFoundException;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.application.WorkspaceSecurityManager;
import com.playprobie.api.infra.gamelift.GameLiftService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.gameliftstreams.model.GetStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.StreamGroupStatus;

/**
 * 스트리밍 리소스 CRUD 및 상태 조회 서비스.
 *
 * <p>
 * 리소스 생성, 조회, 삭제 및 상태 확인을 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StreamingResourceManager {

	private final StreamingResourceRepository streamingResourceRepository;
	private final CapacityChangeRequestRepository capacityChangeRequestRepository;
	private final SelfHealingLogRepository selfHealingLogRepository;
	private final SurveyRepository surveyRepository;
	private final GameBuildRepository gameBuildRepository;
	private final GameLiftService gameLiftService;
	private final WorkspaceSecurityManager securityManager;
	private final StreamingProvisioner streamingProvisioner;
	private final StreamingHealthMonitor streamingHealthMonitor;

	// ========== 리소스 CRUD ==========

	/**
	 * 스트리밍 리소스를 생성합니다.
	 *
	 * @param surveyUuid Survey UUID
	 * @param request    생성 요청
	 * @param user       요청 사용자
	 * @return 생성된 리소스 정보 (상태: CREATING)
	 */
	@Transactional
	public StreamingResourceResponse createResource(UUID surveyUuid,
		CreateStreamingResourceRequest request, User user) {
		log.info("Creating streaming resource for surveyUuid={}, buildId={}", surveyUuid, request.buildId());

		// 1. Survey 조회
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);

		// Security Check
		securityManager.validateWriteAccess(survey.getGame().getWorkspace(), user);

		Long surveyId = survey.getId();

		// 2. 중복 생성 방지
		validateResourceDuplication(surveyId);

		// 3. GameBuild 조회
		GameBuild build = gameBuildRepository.findByUuid(request.buildId())
			.orElseThrow(GameBuildNotFoundException::new);

		// 4. StreamClass 호환성 검증
		if (!StreamClass.isCompatible(request.instanceType(), build.getOsType())) {
			throw new StreamClassIncompatibleException(request.instanceType(), build.getOsType());
		}

		// 5. StreamingResource 생성 (CREATING 상태)
		StreamingResource resource = StreamingResource.builder()
			.survey(survey)
			.build(build)
			.instanceType(request.instanceType())
			.maxCapacity(request.maxCapacity())
			.build();

		streamingResourceRepository.save(resource);

		// 6. 비동기 프로비저닝 시작 (트랜잭션 커밋 후 실행)
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				streamingProvisioner.provisionResourceAsync(resource.getId());
			}
		});

		log.info("Streaming resource creation initiated: resourceId={}, status={}",
			resource.getId(), resource.getStatus());

		return StreamingResourceResponse.from(resource);
	}

	private void validateResourceDuplication(Long surveyId) {
		streamingResourceRepository.findBySurveyId(surveyId).ifPresent(resource -> {
			if (!resource.getStatus().isTerminatedOrCleaning()) {
				throw new StreamingResourceAlreadyExistsException();
			}
		});
	}

	/**
	 * 스트리밍 리소스를 조회합니다.
	 *
	 * @param surveyId Survey PK
	 * @param user     요청 사용자
	 * @return 리소스 정보
	 */
	public StreamingResourceResponse getResource(Long surveyId, User user) {
		StreamingResource resource = streamingResourceRepository.findBySurveyId(surveyId)
			.orElseThrow(StreamingResourceNotFoundException::new);
		securityManager.validateReadAccess(resource.getSurvey().getGame().getWorkspace(), user);
		return StreamingResourceResponse.from(resource);
	}

	/**
	 * 스트리밍 리소스를 UUID로 조회합니다 (JIT 동기화 포함).
	 *
	 * @param uuid Survey UUID
	 * @param user 요청 사용자
	 * @return 리소스 정보
	 */
	@Transactional
	public StreamingResourceResponse getResourceByUuid(UUID uuid, User user) {
		StreamingResource resource = streamingResourceRepository.findBySurvey_Uuid(uuid)
			.orElseThrow(StreamingResourceNotFoundException::new);
		securityManager.validateReadAccess(resource.getSurvey().getGame().getWorkspace(), user);

		// JIT 상태 동기화 (Transitional State일 때만 수행)
		if (resource.getAwsStreamGroupId() != null &&
			streamingHealthMonitor.isTransitionalState(resource.getStatus())) {
			streamingHealthMonitor.synchronizeAndHeal(resource);
		}

		return StreamingResourceResponse.from(resource);
	}

	/**
	 * 스트리밍 리소스를 삭제합니다.
	 *
	 * @param surveyUuid Survey UUID
	 * @param user       요청 사용자
	 */
	@Transactional
	public void deleteResource(UUID surveyUuid, User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);
		deleteResource(survey.getId(), user);
	}

	/**
	 * 리소스가 존재할 경우에만 삭제를 수행합니다. (Safe Method)
	 *
	 * <p>
	 * 트랜잭션 롤백 방지를 위해 예외 대신 존재 여부를 확인합니다.
	 *
	 * @param surveyUuid Survey UUID
	 * @param user       요청 사용자
	 */
	@Transactional
	public void deleteResourceIfPresent(UUID surveyUuid, User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);

		streamingResourceRepository.findBySurveyId(survey.getId())
			.ifPresent(resource -> deleteResourceInternal(resource, user));
	}

	@Transactional
	public void deleteResource(Long surveyId, User user) {
		log.info("Deleting streaming resource for surveyId={}", surveyId);

		StreamingResource resource = streamingResourceRepository.findBySurveyId(surveyId)
			.orElseThrow(StreamingResourceNotFoundException::new);

		deleteResourceInternal(resource, user);
	}

	private void deleteResourceInternal(StreamingResource resource, User user) {
		securityManager.validateWriteAccess(resource.getSurvey().getGame().getWorkspace(), user);

		Long resourceId = resource.getId();

		// 1. FK 제약조건 해결: 관련 CapacityChangeRequest 먼저 삭제
		capacityChangeRequestRepository.deleteByResourceId(resourceId);

		// 2. SelfHealingLog 삭제 (FK 제약조건 없지만 정리 목적)
		selfHealingLogRepository.findByResourceId(resourceId)
			.ifPresent(selfHealingLogRepository::delete);

		// 3. AWS 리소스 삭제
		if (resource.getAwsStreamGroupId() != null) {
			gameLiftService.deleteStreamGroup(resource.getAwsStreamGroupId());
		}
		if (resource.getAwsApplicationId() != null) {
			gameLiftService.deleteApplication(resource.getAwsApplicationId());
		}

		// 4. StreamingResource 삭제
		resource.terminate();
		streamingResourceRepository.delete(resource);

		log.info("Streaming resource deleted: resourceId={}", resourceId);
	}

	// ========== 상태 조회 ==========

	/**
	 * 리소스 상태를 조회합니다 (실시간 AWS API 호출).
	 *
	 * @param surveyId Survey PK
	 * @param user     요청 사용자
	 * @return 리소스 상태
	 */
	@Transactional
	public ResourceStatusResponse getResourceStatus(Long surveyId, User user) {
		StreamingResource resource = streamingResourceRepository.findBySurveyId(surveyId)
			.orElseThrow(StreamingResourceNotFoundException::new);
		securityManager.validateReadAccess(resource.getSurvey().getGame().getWorkspace(), user);

		// AWS API 호출 및 상태 동기화 (Transitional State일 때만 수행)
		boolean instancesReady = false;
		if (resource.getAwsStreamGroupId() != null &&
			streamingHealthMonitor.isTransitionalState(resource.getStatus())) {
			try {
				GetStreamGroupResponse awsResponse = streamingHealthMonitor.synchronizeAndHeal(resource);
				if (awsResponse != null) {
					instancesReady = awsResponse.status() == StreamGroupStatus.ACTIVE;
				}
			} catch (Exception e) {
				log.warn("Failed to sync status with AWS for resourceId={}: {}",
					resource.getId(), e.getMessage());
			}
		} else {
			// Stable State라면 DB 정보 기반으로 ready 여부 판단
			instancesReady = resource.getStatus() == StreamingResourceStatus.ACTIVE;
		}

		return ResourceStatusResponse.of(
			resource.getStatus().name(),
			resource.getCurrentCapacity(),
			instancesReady);
	}

	/**
	 * 리소스 상태를 조회합니다 (UUID 버전).
	 *
	 * @param surveyUuid Survey UUID
	 * @param user       요청 사용자
	 * @return 리소스 상태
	 */
	public ResourceStatusResponse getResourceStatus(UUID surveyUuid, User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(SurveyNotFoundException::new);
		return getResourceStatus(survey.getId(), user);
	}
}
