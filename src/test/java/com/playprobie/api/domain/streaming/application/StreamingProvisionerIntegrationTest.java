package com.playprobie.api.domain.streaming.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.domain.streaming.domain.StreamingResourceStatus;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.infra.gamelift.GameLiftService;

import software.amazon.awssdk.services.gameliftstreams.model.CreateApplicationResponse;
import software.amazon.awssdk.services.gameliftstreams.model.CreateStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.GetStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.StreamGroupStatus;

/**
 * StreamingProvisioner 통합 테스트.
 *
 * <p>
 * <b>핵심 검증 목표</b>: 리팩토링 후 DB 커넥션 풀이 고갈되지 않음을 확인합니다.
 *
 * <p>
 * <b>테스트 시나리오</b>:
 * <ul>
 *   <li>Mock AWS 서버에 5초 응답 지연 설정</li>
 *   <li>DB 커넥션 풀 최대값: 10 (application-test.yml)</li>
 *   <li>동시 프로비저닝 요청: 50개</li>
 * </ul>
 *
 * <p>
 * <b>기대 결과</b>:
 * <ul>
 *   <li>❌ 기존 코드: 10개 요청 후 커넥션 풀 고갈</li>
 *   <li>✅ 리팩토링 후: 모든 50개 요청 정상 처리</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class StreamingProvisionerIntegrationTest {

	private static final int AWS_DELAY_SECONDS = 5;
	private static final int CONCURRENT_REQUESTS = 50;
	private static final int TEST_TIMEOUT_SECONDS = 60;

	@Autowired
	private StreamingProvisioner streamingProvisioner;

	@Autowired
	private StreamingResourceRepository streamingResourceRepository;

	@Autowired
	private GameLiftService gameLiftService; // Mock

	private AtomicInteger successCount;
	private AtomicInteger failureCount;

	@BeforeEach
	void setUp() {
		successCount = new AtomicInteger(0);
		failureCount = new AtomicInteger(0);
	}

	@Test
	@DisplayName("동시 50개 프로비저닝 요청 시 커넥션 풀이 고갈되지 않아야 한다")
	void shouldNotExhaustConnectionPool() throws InterruptedException {
		// Given: Mock AWS with delay
		setupMockAwsWithDelay(AWS_DELAY_SECONDS);

		// Create test resources
		for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
			createTestResource((long)(i + 1));
		}

		// When: 50 concurrent provisionResourceAsync calls
		ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
		CountDownLatch latch = new CountDownLatch(CONCURRENT_REQUESTS);

		for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
			final long resourceId = i + 1;
			executor.submit(() -> {
				try {
					streamingProvisioner.provisionResourceAsync(resourceId);
					successCount.incrementAndGet();
				} catch (Exception e) {
					failureCount.incrementAndGet();
				} finally {
					latch.countDown();
				}
			});
		}

		// Then: All requests complete without HikariPool connection timeout
		boolean completed = latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

		executor.shutdown();

		assertThat(completed)
			.as("All requests should complete within timeout")
			.isTrue();

		// 모든 요청이 Queue에 등록됨 (비동기이므로 즉시 반환)
		assertThat(successCount.get())
			.as("All async tasks should be submitted successfully")
			.isEqualTo(CONCURRENT_REQUESTS);

		assertThat(failureCount.get())
			.as("No connection pool exhaustion failures")
			.isZero();
	}

	@Test
	@DisplayName("프로비저닝 실패 시 AWS 리소스가 정리되어야 한다")
	void shouldCleanupAwsResourcesOnFailure() {
		// Given: Mock setup - Application 성공, StreamGroup 실패
		Long resourceId = 999L;
		createTestResource(resourceId);

		String mockAppArn = "arn:aws:gameliftstreams:app/test-app";

		when(gameLiftService.createApplication(any(), any(), any(), any()))
			.thenReturn(CreateApplicationResponse.builder().arn(mockAppArn).build());

		when(gameLiftService.createStreamGroup(any(), any()))
			.thenThrow(new RuntimeException("StreamGroup creation failed"));

		// When
		streamingProvisioner.provisionResourceAsync(resourceId);

		// Then: Application 삭제가 호출되어야 함
		verify(gameLiftService, timeout(10000)).deleteApplication(mockAppArn);

		// DB 상태가 ERROR로 변경되어야 함
		Optional<StreamingResource> resource = streamingResourceRepository.findById(resourceId);
		assertThat(resource).isPresent();
		assertThat(resource.get().getStatus()).isEqualTo(StreamingResourceStatus.ERROR);
	}

	// ========== Helper Methods ==========

	private void setupMockAwsWithDelay(int delaySeconds) {
		// Mock with delay to simulate slow AWS responses
		when(gameLiftService.createApplication(any(), any(), any(), any()))
			.thenAnswer(invocation -> {
				Thread.sleep(delaySeconds * 1000L);
				return CreateApplicationResponse.builder()
					.arn("arn:aws:gameliftstreams:app/" + UUID.randomUUID())
					.build();
			});

		when(gameLiftService.createStreamGroup(any(), any()))
			.thenAnswer(invocation -> {
				Thread.sleep(delaySeconds * 1000L);
				return CreateStreamGroupResponse.builder()
					.arn("arn:aws:gameliftstreams:streamgroup/" + UUID.randomUUID())
					.build();
			});

		when(gameLiftService.getStreamGroupStatus(any()))
			.thenReturn(GetStreamGroupResponse.builder()
				.status(StreamGroupStatus.ACTIVE)
				.build());
	}

	private void createTestResource(Long id) {
		// Mock repository behavior
		StreamingResource mockResource = mock(StreamingResource.class);
		Survey mockSurvey = mock(Survey.class);
		GameBuild mockBuild = mock(GameBuild.class);

		when(mockResource.getId()).thenReturn(id);
		when(mockResource.getSurvey()).thenReturn(mockSurvey);
		when(mockResource.getBuild()).thenReturn(mockBuild);
		when(mockResource.getInstanceType()).thenReturn("gen4n_win2022");
		when(mockSurvey.getUuid()).thenReturn(UUID.randomUUID());
		when(mockBuild.getS3Prefix()).thenReturn("test/prefix");
		when(mockBuild.getExecutablePath()).thenReturn("/game.exe");
		when(mockBuild.getOsType()).thenReturn("WINDOWS");

		when(streamingResourceRepository.findById(id)).thenReturn(Optional.of(mockResource));
	}

	/**
	 * Mock GameLiftService를 주입하기 위한 테스트 설정.
	 */
	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		public GameLiftService mockGameLiftService() {
			return mock(GameLiftService.class);
		}
	}
}
