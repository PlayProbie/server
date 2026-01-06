package com.playprobie.api.domain.streaming.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.dao.GameBuildRepository;
import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.game.exception.GameBuildNotFoundException;
import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.SessionStatus;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.model.StreamClass;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.domain.streaming.dto.CreateStreamingResourceRequest;
import com.playprobie.api.domain.streaming.dto.ResourceStatusResponse;
import com.playprobie.api.domain.streaming.dto.SessionAvailabilityResponse;
import com.playprobie.api.domain.streaming.dto.SignalResponse;
import com.playprobie.api.domain.streaming.dto.StreamingResourceResponse;
import com.playprobie.api.domain.streaming.dto.TestActionResponse;
import com.playprobie.api.domain.streaming.exception.StreamClassIncompatibleException;
import com.playprobie.api.domain.streaming.exception.StreamingResourceAlreadyExistsException;
import com.playprobie.api.domain.streaming.exception.StreamingResourceNotFoundException;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.domain.SurveyStatus;
import com.playprobie.api.domain.survey.exception.SurveyNotFoundException;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;
import com.playprobie.api.infra.gamelift.GameLiftService;
import com.playprobie.api.infra.config.AwsProperties;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.application.WorkspaceSecurityManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.gameliftstreams.model.CreateApplicationResponse;
import software.amazon.awssdk.services.gameliftstreams.model.CreateStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.GetStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.StartStreamSessionResponse;
import software.amazon.awssdk.services.gameliftstreams.model.StreamGroupStatus;

/**
 * 스트리밍 리소스 관리 서비스.
 * 
 * <p>
 * JIT Provisioning 워크플로우의 비즈니스 로직을 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StreamingResourceService {

        private final StreamingResourceRepository streamingResourceRepository;
        private final SurveyRepository surveyRepository;
        private final GameBuildRepository gameBuildRepository;
        private final SurveySessionRepository surveySessionRepository;
        private final GameLiftService gameLiftService;
        private final AwsProperties awsProperties;
        private final WorkspaceSecurityManager securityManager;

        // ========== Admin: Resource Management ==========

        /**
         * 스트리밍 리소스를 할당합니다.
         * 
         * @param surveyId Survey PK
         * @param request  할당 요청
         * @return 생성된 리소스 정보
         */
        @Transactional
        public StreamingResourceResponse createResource(java.util.UUID surveyUuid,
                        CreateStreamingResourceRequest request, User user) {
                log.info("Creating streaming resource for surveyUuid={}, buildId={}", surveyUuid, request.buildId());

                // 1. Survey 조회
                Survey survey = surveyRepository.findByUuid(surveyUuid)
                                .orElseThrow(SurveyNotFoundException::new);

                // Security Check
                securityManager.validateWriteAccess(survey.getGame().getWorkspace(), user);

                Long surveyId = survey.getId();

                // 2. 기존 리소스 존재 여부 확인
                if (streamingResourceRepository.existsBySurveyId(surveyId)) {
                        throw new StreamingResourceAlreadyExistsException();
                }

                // Call legacy logic or continue implementation. Since this is a refactor, I
                // will reuse the logic by getting the Survey entity first.
                // But to avoid code duplication, I should ideally extract the core logic.
                // For now, I will implement the UUID version fully or delegate if possible.
                // However, the original creation logic relies on `surveyRepository.findById`.
                // Using the retrieved `survey` entity covers the requirement.

                // 3. GameBuild 조회
                GameBuild build = gameBuildRepository.findByUuid(request.buildId())
                                .orElseThrow(GameBuildNotFoundException::new);

                // 4. StreamClass 호환성 검증
                if (!StreamClass.isCompatible(request.instanceType(), build.getOsType())) {
                        throw new StreamClassIncompatibleException(request.instanceType(), build.getOsType());
                }

                // 5. StreamingResource 생성 (PENDING 상태)
                StreamingResource resource = StreamingResource.builder()
                                .survey(survey)
                                .build(build)
                                .instanceType(request.instanceType())
                                .maxCapacity(request.maxCapacity())
                                .build();
                streamingResourceRepository.save(resource);

                // 6. AWS Application 생성
                String s3Uri = String.format("s3://%s/%s", awsProperties.getS3().getBucketName(), build.getS3Prefix());
                CreateApplicationResponse appResponse = gameLiftService.createApplication(
                                survey.getName() + "-app",
                                s3Uri,
                                build.getExecutablePath(),
                                build.getOsType());

                resource.assignApplication(appResponse.arn());
                streamingResourceRepository.save(resource);

                // 7. AWS StreamGroup 생성
                CreateStreamGroupResponse groupResponse = gameLiftService.createStreamGroup(
                                survey.getName() + "-group",
                                request.instanceType());

                // 8. Application을 StreamGroup에 연결 (상태 안정화 대기)
                waitForStreamGroupStable(groupResponse.arn());
                gameLiftService.associateApplication(groupResponse.arn(), appResponse.arn());

                resource.assignStreamGroup(groupResponse.arn());
                streamingResourceRepository.save(resource);

                log.info("Streaming resource created: resourceId={}, appArn={}, groupArn={}",
                                resource.getId(), appResponse.arn(), groupResponse.arn());

                return StreamingResourceResponse.from(resource);
        }

        /**
         * 스트리밍 리소스를 조회합니다.
         * 
         * @param surveyId Survey PK
         * @return 리소스 정보
         */
        public StreamingResourceResponse getResource(Long surveyId, User user) {
                StreamingResource resource = streamingResourceRepository.findBySurveyId(surveyId)
                                .orElseThrow(StreamingResourceNotFoundException::new);
                securityManager.validateReadAccess(resource.getSurvey().getGame().getWorkspace(), user);
                return StreamingResourceResponse.from(resource);
        }

        /**
         * 스트리밍 리소스를 UUID로 조회합니다.
         */
        public StreamingResourceResponse getResourceByUuid(UUID uuid, User user) {
                StreamingResource resource = streamingResourceRepository.findBySurveyUuid(uuid)
                                .orElseThrow(StreamingResourceNotFoundException::new);
                securityManager.validateReadAccess(resource.getSurvey().getGame().getWorkspace(), user);
                return StreamingResourceResponse.from(resource);
        }

        /**
         * 스트리밍 리소스를 삭제합니다.
         * 
         * @param surveyId Survey PK
         */
        @Transactional
        public void deleteResource(java.util.UUID surveyUuid, User user) {
                Survey survey = surveyRepository.findByUuid(surveyUuid)
                                .orElseThrow(SurveyNotFoundException::new);
                // Security Check inside deleteResource(Long, User) or here?
                // Better here to avoid fetch in overload if possible, but overload does fetch
                // too.
                // Let's pass user to overload.
                deleteResource(survey.getId(), user);
        }

        @Transactional
        public void deleteResource(Long surveyId, User user) {
                log.info("Deleting streaming resource for surveyId={}", surveyId);

                StreamingResource resource = streamingResourceRepository.findBySurveyId(surveyId)
                                .orElseThrow(StreamingResourceNotFoundException::new);

                securityManager.validateWriteAccess(resource.getSurvey().getGame().getWorkspace(), user);

                // AWS 리소스 삭제
                if (resource.getAwsStreamGroupId() != null) {
                        gameLiftService.deleteStreamGroup(resource.getAwsStreamGroupId());
                }
                if (resource.getAwsApplicationId() != null) {
                        gameLiftService.deleteApplication(resource.getAwsApplicationId());
                }

                resource.terminate();
                streamingResourceRepository.delete(resource);

                log.info("Streaming resource deleted: resourceId={}", resource.getId());
        }

        // ========== Admin: Test Management ==========

        /**
         * 관리자 테스트를 시작합니다 (Capacity 0 → 1).
         * 
         * @param surveyId Survey PK
         * @return 테스트 상태
         */
        @Transactional
        public TestActionResponse startTest(Long surveyId, User user) {
                log.info("Starting test for surveyId={}", surveyId);

                StreamingResource resource = streamingResourceRepository.findBySurveyId(surveyId)
                                .orElseThrow(StreamingResourceNotFoundException::new);
                securityManager.validateWriteAccess(resource.getSurvey().getGame().getWorkspace(), user);

                // Capacity를 1로 변경
                gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), 1);

                resource.startTest();
                streamingResourceRepository.save(resource);

                log.info("Test started for resourceId={}", resource.getId());
                return TestActionResponse.startTest(resource.getStatus().name(), resource.getCurrentCapacity());
        }

        /**
         * 설문을 활성화합니다 (Capacity 0 → Max Capacity).
         */
        @Transactional
        public TestActionResponse activateResource(java.util.UUID surveyUuid, User user) {
                log.info("Activating resource for surveyUuid={}", surveyUuid);

                Survey survey = surveyRepository.findByUuid(surveyUuid)
                                .orElseThrow(SurveyNotFoundException::new);

                StreamingResource resource = streamingResourceRepository.findBySurveyId(survey.getId())
                                .orElseThrow(StreamingResourceNotFoundException::new);
                securityManager.validateWriteAccess(resource.getSurvey().getGame().getWorkspace(), user);

                // Capacity를 maxCapacity로 변경
                gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), resource.getMaxCapacity());

                resource.activate(resource.getMaxCapacity());
                streamingResourceRepository.save(resource);

                log.info("Resource activated for resourceId={}, capacity={}", resource.getId(),
                                resource.getMaxCapacity());
                return TestActionResponse.startTest("SCALING", resource.getMaxCapacity());
        }

        @Transactional
        public TestActionResponse startTest(java.util.UUID surveyUuid, User user) {
                Survey survey = surveyRepository.findByUuid(surveyUuid)
                                .orElseThrow(SurveyNotFoundException::new);
                return startTest(survey.getId(), user);
        }

        /**
         * 관리자 테스트를 종료합니다 (Capacity 1 → 0).
         * 
         * @param surveyId Survey PK
         * @return 테스트 상태
         */
        @Transactional
        public TestActionResponse stopTest(Long surveyId, User user) {
                log.info("Stopping test for surveyId={}", surveyId);

                StreamingResource resource = streamingResourceRepository.findBySurveyId(surveyId)
                                .orElseThrow(StreamingResourceNotFoundException::new);
                securityManager.validateWriteAccess(resource.getSurvey().getGame().getWorkspace(), user);

                // Capacity를 0으로 변경
                gameLiftService.updateStreamGroupCapacity(resource.getAwsStreamGroupId(), 0);

                resource.stopTest();
                streamingResourceRepository.save(resource);

                log.info("Test stopped for resourceId={}", resource.getId());
                return TestActionResponse.stopTest(resource.getStatus().name(), resource.getCurrentCapacity());
        }

        @Transactional
        public TestActionResponse stopTest(java.util.UUID surveyUuid, User user) {
                Survey survey = surveyRepository.findByUuid(surveyUuid)
                                .orElseThrow(SurveyNotFoundException::new);
                return stopTest(survey.getId(), user);
        }

        /**
         * 리소스 상태를 조회합니다 (실시간 AWS API 호출).
         * 
         * @param surveyId Survey PK
         * @return 리소스 상태
         */
        public ResourceStatusResponse getResourceStatus(Long surveyId, User user) {
                StreamingResource resource = streamingResourceRepository.findBySurveyId(surveyId)
                                .orElseThrow(StreamingResourceNotFoundException::new);
                securityManager.validateReadAccess(resource.getSurvey().getGame().getWorkspace(), user);

                // AWS API 호출하여 인스턴스 준비 상태 확인
                boolean instancesReady = false;
                if (resource.getAwsStreamGroupId() != null) {
                        GetStreamGroupResponse awsResponse = gameLiftService.getStreamGroupStatus(
                                        resource.getAwsStreamGroupId());
                        instancesReady = awsResponse.status() == StreamGroupStatus.ACTIVE;
                }

                return ResourceStatusResponse.of(
                                resource.getStatus().name(),
                                resource.getCurrentCapacity(),
                                instancesReady);
        }

        public ResourceStatusResponse getResourceStatus(java.util.UUID surveyUuid, User user) {
                Survey survey = surveyRepository.findByUuid(surveyUuid)
                                .orElseThrow(SurveyNotFoundException::new);
                return getResourceStatus(survey.getId(), user);
        }

        // ========== Tester Session Management ==========

        /**
         * 세션 가용성을 확인합니다.
         * 
         * @param surveyUuid Survey UUID
         * @return 세션 가용성
         */
        public SessionAvailabilityResponse checkSessionAvailability(UUID surveyUuid) {
                Survey survey = surveyRepository.findByUuid(surveyUuid)
                                .orElseThrow(SurveyNotFoundException::new);

                // 설문 상태 확인
                if (survey.getStatus() != SurveyStatus.ACTIVE) {
                        return SessionAvailabilityResponse.unavailable(surveyUuid, survey.getGame().getName());
                }

                // 리소스 확인
                StreamingResource resource = streamingResourceRepository.findBySurveyId(survey.getId())
                                .orElse(null);

                if (resource == null || !resource.getStatus().isAvailable()) {
                        return SessionAvailabilityResponse.unavailable(surveyUuid, survey.getGame().getName());
                }

                // TODO: 실제 Capacity 대비 활성 세션 수 비교하여 가용성 판단
                return SessionAvailabilityResponse.available(surveyUuid, survey.getGame().getName());
        }

        /**
         * WebRTC 시그널링을 처리합니다.
         * 
         * @param surveyUuid    Survey UUID
         * @param signalRequest 시그널 요청 (Base64)
         * @return 시그널 응답
         */
        @Transactional
        public SignalResponse processSignal(UUID surveyUuid, String signalRequest) {
                log.info("Processing signal for surveyUuid={}", surveyUuid);

                Survey survey = surveyRepository.findByUuid(surveyUuid)
                                .orElseThrow(SurveyNotFoundException::new);

                // 리소스 조회
                StreamingResource resource = streamingResourceRepository.findBySurveyId(survey.getId())
                                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_AVAILABLE));

                if (!resource.getStatus().isAvailable()) {
                        throw new BusinessException(ErrorCode.RESOURCE_NOT_AVAILABLE);
                }

                // SurveySession 생성
                SurveySession session = SurveySession.builder()
                                .survey(survey)
                                .build();
                surveySessionRepository.save(session);

                // AWS StartStreamSession 호출
                StartStreamSessionResponse awsResponse = gameLiftService.startStreamSession(
                                resource.getAwsStreamGroupId(),
                                resource.getAwsApplicationId(),
                                signalRequest);

                // 세션에 AWS 세션 ID 연결
                session.connect(awsResponse.arn());
                surveySessionRepository.save(session);

                log.info("Signal processed: sessionUuid={}, awsSessionArn={}", session.getUuid(), awsResponse.arn());

                return SignalResponse.of(awsResponse.signalResponse(), session.getUuid());
        }

        /**
         * 세션을 종료합니다.
         * 
         * @param surveyUuid        Survey UUID
         * @param surveySessionUuid Session UUID
         * @param reason            종료 사유
         */
        @Transactional
        public void terminateSession(UUID surveyUuid, UUID surveySessionUuid, String reason) {
                log.info("Terminating session: surveyUuid={}, sessionUuid={}, reason={}",
                                surveyUuid, surveySessionUuid, reason);

                SurveySession session = surveySessionRepository.findByUuid(surveySessionUuid)
                                .orElseThrow(() -> new BusinessException(ErrorCode.SURVEY_SESSION_NOT_FOUND));

                // 이미 종료된 세션인지 확인
                if (session.getStatus().isTerminated()) {
                        return;
                }

                // 리소스 조회
                StreamingResource resource = streamingResourceRepository.findBySurveyId(session.getSurvey().getId())
                                .orElse(null);

                // AWS 세션 종료
                if (resource != null && session.getAwsSessionId() != null) {
                        gameLiftService.terminateStreamSession(
                                        resource.getAwsStreamGroupId(),
                                        session.getAwsSessionId());
                }

                session.terminate();
                surveySessionRepository.save(session);

                log.info("Session terminated: sessionUuid={}", surveySessionUuid);
        }

        /**
         * 세션 상태를 확인합니다 (Heartbeat).
         * 
         * @param surveySessionUuid Session UUID
         * @return 세션 활성 여부
         */
        public boolean isSessionActive(UUID surveySessionUuid) {
                return surveySessionRepository.findByUuid(surveySessionUuid)
                                .map(session -> session.getStatus() == SessionStatus.CONNECTED)
                                .orElse(false);
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
