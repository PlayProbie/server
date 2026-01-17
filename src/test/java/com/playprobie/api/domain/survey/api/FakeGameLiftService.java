package com.playprobie.api.domain.survey.api;

import java.util.ArrayList;
import java.util.List;

import com.playprobie.api.infra.gamelift.GameLiftService;

import software.amazon.awssdk.services.gameliftstreams.model.CreateApplicationResponse;
import software.amazon.awssdk.services.gameliftstreams.model.CreateStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.CreateStreamSessionConnectionResponse;
import software.amazon.awssdk.services.gameliftstreams.model.GetStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.StartStreamSessionResponse;
import software.amazon.awssdk.services.gameliftstreams.model.StreamGroupStatus;

/**
 * 테스트용 FakeGameLiftService.
 *
 * <p>
 * AWS GameLift API를 호출하지 않고, 호출 기록만 저장합니다.
 * 상태 검증(State Verification) 패턴에 적합합니다.
 */
public class FakeGameLiftService extends GameLiftService {

	private final List<String> deletedStreamGroups = new ArrayList<>();
	private final List<String> deletedApplications = new ArrayList<>();
	private final List<CapacityUpdateRecord> capacityUpdates = new ArrayList<>();

	public FakeGameLiftService() {
		super(null, null); // AWS client와 properties 사용 안함
	}

	@Override
	public CreateApplicationResponse createApplication(
		String applicationName, String s3Uri, String executablePath, String osType) {
		return CreateApplicationResponse.builder()
			.arn("arn:aws:gamelift:fake:application:" + applicationName)
			.build();
	}

	@Override
	public CreateStreamGroupResponse createStreamGroup(String groupName, String streamClassValue) {
		return CreateStreamGroupResponse.builder()
			.arn("arn:aws:gamelift:fake:streamgroup:" + groupName)
			.build();
	}

	@Override
	public void associateApplication(String streamGroupId, String applicationId) {
		// No-op
	}

	@Override
	public void updateStreamGroupCapacity(String streamGroupId, int targetCapacity) {
		capacityUpdates.add(new CapacityUpdateRecord(streamGroupId, targetCapacity));
	}

	@Override
	public GetStreamGroupResponse getStreamGroupStatus(String streamGroupId) {
		return GetStreamGroupResponse.builder()
			.arn(streamGroupId)
			.status(StreamGroupStatus.ACTIVE)
			.build();
	}

	@Override
	public StartStreamSessionResponse startStreamSession(
		String streamGroupId, String applicationId, String signalRequest) {
		return StartStreamSessionResponse.builder()
			.arn("arn:aws:gamelift:fake:session:" + java.util.UUID.randomUUID())
			.signalResponse("fake-signal-response")
			.build();
	}

	@Override
	public CreateStreamSessionConnectionResponse createStreamSessionConnection(
		String streamGroupId, String streamSessionId, String signalRequest) {
		return CreateStreamSessionConnectionResponse.builder()
			.signalResponse("fake-signal-response")
			.build();
	}

	@Override
	public void terminateStreamSession(String streamGroupId, String sessionId) {
		// No-op
	}

	@Override
	public void deleteStreamGroup(String streamGroupId) {
		deletedStreamGroups.add(streamGroupId);
	}

	@Override
	public void deleteApplication(String applicationId) {
		deletedApplications.add(applicationId);
	}

	// ========== 상태 검증용 메서드 ==========

	public List<String> getDeletedStreamGroups() {
		return deletedStreamGroups;
	}

	public List<String> getDeletedApplications() {
		return deletedApplications;
	}

	public List<CapacityUpdateRecord> getCapacityUpdates() {
		return capacityUpdates;
	}

	public void reset() {
		deletedStreamGroups.clear();
		deletedApplications.clear();
		capacityUpdates.clear();
	}

	public record CapacityUpdateRecord(String streamGroupId, int targetCapacity) {
	}
}
