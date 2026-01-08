package com.playprobie.api.infra.gamelift;

import org.springframework.stereotype.Service;

import com.playprobie.api.global.config.properties.AwsProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.gameliftstreams.GameLiftStreamsClient;
import software.amazon.awssdk.services.gameliftstreams.model.AssociateApplicationsRequest;
import software.amazon.awssdk.services.gameliftstreams.model.CreateApplicationRequest;
import software.amazon.awssdk.services.gameliftstreams.model.CreateApplicationResponse;
import software.amazon.awssdk.services.gameliftstreams.model.CreateStreamGroupRequest;
import software.amazon.awssdk.services.gameliftstreams.model.CreateStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.DeleteApplicationRequest;
import software.amazon.awssdk.services.gameliftstreams.model.DeleteStreamGroupRequest;
import software.amazon.awssdk.services.gameliftstreams.model.GetStreamGroupRequest;
import software.amazon.awssdk.services.gameliftstreams.model.GetStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.LocationConfiguration;
import software.amazon.awssdk.services.gameliftstreams.model.Protocol;
import software.amazon.awssdk.services.gameliftstreams.model.RuntimeEnvironment;
import software.amazon.awssdk.services.gameliftstreams.model.StartStreamSessionRequest;
import software.amazon.awssdk.services.gameliftstreams.model.StartStreamSessionResponse;
import software.amazon.awssdk.services.gameliftstreams.model.StreamClass;
import software.amazon.awssdk.services.gameliftstreams.model.TerminateStreamSessionRequest;
import software.amazon.awssdk.services.gameliftstreams.model.UpdateStreamGroupRequest;

/**
 * AWS GameLift Streams 연동 서비스.
 *
 * <p>
 * JIT Provisioning 워크플로우의 AWS 리소스 관리를 담당합니다.
 *
 * <p>
 * <b>⚠️ CRITICAL: Cost Safety</b><br>
 * {@link #createStreamGroup} 메서드에서 Capacity는 항상 0으로 하드코딩됩니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameLiftService {

	private static final String OS_WINDOWS = "WINDOWS";
	private static final String OS_UBUNTU = "UBUNTU";
	private static final String RUNTIME_WIN_2022 = "2022";
	private static final String RUNTIME_UBUNTU = "22_04_LTS"; // Renamed for clarity

	private static final int SAFE_CAPACITY = 0; // Cost Safety Guarantee

	private final GameLiftStreamsClient gameLiftStreamsClient;
	private final AwsProperties awsProperties;

	/**
	 * GameLift Application을 생성합니다.
	 *
	 * <p>
	 * S3에 업로드된 게임 빌드를 기반으로 Application을 생성합니다.
	 * 이 작업은 비동기 처리를 권장합니다 (@Async).
	 *
	 * @param applicationName 애플리케이션 표시 이름
	 * @param s3Uri           S3 버킷 URI (s3://bucket/prefix)
	 * @param executablePath  게임 실행 파일 경로
	 * @param osType          운영체제 타입 (WINDOWS / LINUX)
	 * @return 생성된 Application 응답
	 */
	public CreateApplicationResponse createApplication(
		String applicationName,
		String s3Uri,
		String executablePath,
		String osType) {

		log.info("Creating GameLift Application: name={}, s3Uri={}, executablePath={}, osType={}",
			applicationName, s3Uri, executablePath, osType);

		// RuntimeEnvironment 설정 (OS 타입에 따라)
		RuntimeEnvironment.Builder runtimeBuilder = RuntimeEnvironment.builder();
		if (OS_WINDOWS.equals(osType)) {
			runtimeBuilder.type(OS_WINDOWS).version(RUNTIME_WIN_2022);
		} else {
			runtimeBuilder.type(OS_UBUNTU).version(RUNTIME_UBUNTU);
		}

		CreateApplicationRequest request = CreateApplicationRequest.builder()
			.description(applicationName)
			.applicationSourceUri(s3Uri)
			.executablePath(executablePath)
			.runtimeEnvironment(runtimeBuilder.build())
			.build();

		CreateApplicationResponse response = gameLiftStreamsClient.createApplication(request);

		log.info("GameLift Application created: arn={}", response.arn());
		return response;
	}

	/**
	 * StreamGroup을 생성합니다.
	 *
	 * <p>
	 * <b>🚨 SAFETY: Cost Optimization</b><br>
	 * minCapacity와 desiredCapacity는 반드시 0으로 설정됩니다.
	 * 이 값은 외부 입력과 무관하게 하드코딩되어 있습니다.
	 *
	 * @param groupName        StreamGroup 표시 이름
	 * @param streamClassValue Steam Class ID (예: "gen4n_win2022")
	 * @return 생성된 StreamGroup 응답
	 */
	public CreateStreamGroupResponse createStreamGroup(String groupName, String streamClassValue) {
		log.info("Creating StreamGroup: name={}, streamClass={}", groupName, streamClassValue);

		// ⚠️ SAFETY: Cost Optimization - Capacity는 항상 0으로 하드코딩
		// 절대 이 값을 외부 입력으로 변경하지 마세요!
		// ⚠️ SAFETY: Cost Optimization - Capacity는 항상 0으로 하드코딩 (Class-level CONST used)
		// 절대 이 값을 외부 입력으로 변경하지 마세요!
		final int SAFE_ALWAYS_ON_CAPACITY = SAFE_CAPACITY;
		final int SAFE_MAXIMUM_CAPACITY = SAFE_CAPACITY;

		CreateStreamGroupRequest request = CreateStreamGroupRequest.builder()
			.description(groupName)
			.streamClass(StreamClass.fromValue(streamClassValue))
			.locationConfigurations(LocationConfiguration.builder()
				.locationName(awsProperties.gamelift().region())
				// 🚨 SAFETY: Cost Optimization
				.alwaysOnCapacity(SAFE_ALWAYS_ON_CAPACITY)
				.maximumCapacity(SAFE_MAXIMUM_CAPACITY)
				.build())
			.build();

		CreateStreamGroupResponse response = gameLiftStreamsClient.createStreamGroup(request);

		log.info("StreamGroup created: arn={}, capacity=0 (SAFE)", response.arn());
		return response;
	}

	/**
	 * Application을 StreamGroup에 연결합니다.
	 *
	 * @param streamGroupId StreamGroup ARN 또는 ID
	 * @param applicationId Application ARN 또는 ID
	 */
	public void associateApplication(String streamGroupId, String applicationId) {
		log.info("Associating Application to StreamGroup: streamGroupId={}, applicationId={}",
			streamGroupId, applicationId);

		AssociateApplicationsRequest request = AssociateApplicationsRequest.builder()
			.identifier(streamGroupId)
			.applicationIdentifiers(applicationId)
			.build();

		gameLiftStreamsClient.associateApplications(request);

		log.info("Application associated successfully");
	}

	/**
	 * StreamGroup의 Capacity를 업데이트합니다.
	 *
	 * @param streamGroupId  StreamGroup ARN 또는 ID
	 * @param targetCapacity 목표 Capacity
	 */
	public void updateStreamGroupCapacity(String streamGroupId, int targetCapacity) {
		log.info("Updating StreamGroup capacity: streamGroupId={}, targetCapacity={}",
			streamGroupId, targetCapacity);

		UpdateStreamGroupRequest request = UpdateStreamGroupRequest.builder()
			.identifier(streamGroupId)
			.locationConfigurations(LocationConfiguration.builder()
				.locationName(awsProperties.gamelift().region())
				.alwaysOnCapacity(targetCapacity) // 실제 할당할 인스턴스 수
				.maximumCapacity(targetCapacity) // 최대 허용 용량
				.build())
			.build();

		gameLiftStreamsClient.updateStreamGroup(request);

		log.info("StreamGroup capacity updated to: {} (alwaysOn + maximum)", targetCapacity);
	}

	/**
	 * StreamGroup의 현재 상태를 조회합니다.
	 *
	 * @param streamGroupId StreamGroup ARN 또는 ID
	 * @return StreamGroup 상태 정보
	 */
	public GetStreamGroupResponse getStreamGroupStatus(String streamGroupId) {
		GetStreamGroupRequest request = GetStreamGroupRequest.builder()
			.identifier(streamGroupId)
			.build();

		return gameLiftStreamsClient.getStreamGroup(request);
	}

	/**
	 * 스트리밍 세션을 시작합니다 (WebRTC Signaling).
	 *
	 * @param streamGroupId StreamGroup ARN 또는 ID
	 * @param applicationId Application ARN 또는 ID
	 * @param signalRequest 클라이언트의 Signal Request (Base64)
	 * @return 시작된 세션 응답 (Signal Response 포함)
	 */
	public StartStreamSessionResponse startStreamSession(
		String streamGroupId,
		String applicationId,
		String signalRequest) {

		log.info("Starting stream session: streamGroupId={}, applicationId={}",
			streamGroupId, applicationId);

		StartStreamSessionRequest request = StartStreamSessionRequest.builder()
			.identifier(streamGroupId)
			.applicationIdentifier(applicationId)
			.protocol(Protocol.WEB_RTC)
			.signalRequest(signalRequest)
			.build();

		StartStreamSessionResponse response = gameLiftStreamsClient.startStreamSession(request);

		log.info("Stream session started: arn={}", response.arn());
		return response;
	}

	/**
	 * 스트리밍 세션을 종료합니다.
	 *
	 * @param streamGroupId StreamGroup ARN 또는 ID
	 * @param sessionId     종료할 세션 ID
	 */
	public void terminateStreamSession(String streamGroupId, String sessionId) {
		log.info("Terminating stream session: streamGroupId={}, sessionId={}",
			streamGroupId, sessionId);

		TerminateStreamSessionRequest request = TerminateStreamSessionRequest.builder()
			.identifier(streamGroupId)
			.streamSessionIdentifier(sessionId)
			.build();

		gameLiftStreamsClient.terminateStreamSession(request);

		log.info("Stream session terminated: sessionId={}", sessionId);
	}

	/**
	 * StreamGroup을 삭제합니다.
	 *
	 * <p>
	 * 설문 종료(CLOSED) 시 리소스 회수를 위해 호출됩니다.
	 *
	 * @param streamGroupId StreamGroup ARN 또는 ID
	 */
	public void deleteStreamGroup(String streamGroupId) {
		log.info("Deleting StreamGroup: streamGroupId={}", streamGroupId);

		DeleteStreamGroupRequest request = DeleteStreamGroupRequest.builder()
			.identifier(streamGroupId)
			.build();

		gameLiftStreamsClient.deleteStreamGroup(request);

		log.info("StreamGroup deleted: streamGroupId={}", streamGroupId);
	}

	/**
	 * Application을 삭제합니다.
	 *
	 * <p>
	 * 설문 종료(CLOSED) 시 리소스 회수를 위해 호출됩니다.
	 *
	 * @param applicationId Application ARN 또는 ID
	 */
	public void deleteApplication(String applicationId) {
		log.info("Deleting Application: applicationId={}", applicationId);

		DeleteApplicationRequest request = DeleteApplicationRequest.builder()
			.identifier(applicationId)
			.build();

		gameLiftStreamsClient.deleteApplication(request);

		log.info("Application deleted: applicationId={}", applicationId);
	}
}
