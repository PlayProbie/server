package com.playprobie.api.domain.streaming.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.playprobie.api.domain.game.dao.GameBuildRepository;
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.workspace.dao.WorkspaceRepository;
import com.playprobie.api.domain.workspace.domain.Workspace;

import jakarta.persistence.EntityManager;

/**
 * StreamingResource 엔티티의 @Version 낙관적 락 동작 검증 테스트.
 *
 * <p>
 * 동시성 환경에서 낙관적 락이 올바르게 동작하는지 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class StreamingResourceOptimisticLockTest {

	@Autowired
	private StreamingResourceRepository streamingResourceRepository;

	@Autowired
	private SurveyRepository surveyRepository;

	@Autowired
	private GameRepository gameRepository;

	@Autowired
	private GameBuildRepository gameBuildRepository;

	@Autowired
	private WorkspaceRepository workspaceRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate transactionTemplate;

	private Workspace testWorkspace;
	private Game testGame;
	private GameBuild testBuild;

	@BeforeEach
	void setUp() {
		transactionTemplate = new TransactionTemplate(transactionManager);

		// 테스트 데이터 정리 및 생성 (별도 트랜잭션)
		transactionTemplate.execute(status -> {
			streamingResourceRepository.deleteAll();
			surveyRepository.deleteAll();
			gameBuildRepository.deleteAll();
			gameRepository.deleteAll();
			workspaceRepository.deleteAll();

			testWorkspace = workspaceRepository.save(Workspace.create("TestWS", "Description"));

			testGame = gameRepository.save(Game.builder()
				.workspace(testWorkspace)
				.name("TestGame")
				.context("GameContext")
				.build());

			testBuild = GameBuild.builder()
				.game(testGame)
				.uuid(UUID.randomUUID())
				.version("1.0.0")
				.build();
			testBuild.markAsUploaded(100, 1024L, "WINDOWS", "test.exe");
			testBuild = gameBuildRepository.save(testBuild);

			return null;
		});
	}

	@Nested
	@DisplayName("버전 자동 증가 테스트")
	class VersionAutoIncrementTest {

		@Test
		@DisplayName("엔티티 생성 시 version은 0으로 시작한다")
		void initialVersionIsZero() {
			// given & when
			Long resourceId = transactionTemplate.execute(status -> {
				Survey survey = createAndSaveSurvey("Version Test Survey");
				StreamingResource resource = createResource(survey);
				return streamingResourceRepository.save(resource).getId();
			});

			// then
			transactionTemplate.execute(status -> {
				StreamingResource saved = streamingResourceRepository.findById(resourceId).orElseThrow();
				assertThat(saved.getVersion()).isEqualTo(0L);
				return null;
			});
		}

		@Test
		@DisplayName("엔티티 수정 시 version이 자동으로 증가한다")
		void versionIncrementsOnUpdate() {
			// given
			Long resourceId = transactionTemplate.execute(status -> {
				Survey survey = createAndSaveSurvey("Version Increment Survey");
				StreamingResource resource = createResource(survey);
				resource.assignApplication("app-1");
				resource.assignStreamGroup("sg-1");
				return streamingResourceRepository.save(resource).getId();
			});

			// when: 첫 번째 수정 (version 0 → 1)
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				assertThat(resource.getVersion()).isEqualTo(0L);
				resource.markScalingUp(5);
				streamingResourceRepository.saveAndFlush(resource);
				return null;
			});

			// then: version이 1로 증가
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				assertThat(resource.getVersion()).isEqualTo(1L);
				return null;
			});

			// when: 두 번째 수정 (version 1 → 2)
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				resource.markActive();
				streamingResourceRepository.saveAndFlush(resource);
				return null;
			});

			// then: version이 2로 증가
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				assertThat(resource.getVersion()).isEqualTo(2L);
				return null;
			});
		}
	}

	@Nested
	@DisplayName("동시성 충돌 테스트")
	class ConcurrentModificationTest {

		@Test
		@DisplayName("동일 리소스를 두 트랜잭션에서 동시 수정 시 ObjectOptimisticLockingFailureException이 발생한다")
		void concurrentModificationThrowsException() throws InterruptedException {
			// given: 리소스 생성
			Long resourceId = transactionTemplate.execute(status -> {
				Survey survey = createAndSaveSurvey("Concurrent Test Survey");
				StreamingResource resource = createResource(survey);
				resource.assignApplication("app-concurrent");
				resource.assignStreamGroup("sg-concurrent");
				return streamingResourceRepository.save(resource).getId();
			});

			// 동기화를 위한 래치
			CountDownLatch readyLatch = new CountDownLatch(2);
			CountDownLatch startLatch = new CountDownLatch(1);

			AtomicBoolean txASuccess = new AtomicBoolean(false);
			AtomicBoolean txBSuccess = new AtomicBoolean(false);
			AtomicReference<Exception> txAException = new AtomicReference<>();
			AtomicReference<Exception> txBException = new AtomicReference<>();

			ExecutorService executor = Executors.newFixedThreadPool(2);

			// 트랜잭션 A: 리소스 로드 후 상태 변경
			executor.submit(() -> {
				try {
					transactionTemplate.execute(status -> {
						StreamingResource resourceA = streamingResourceRepository.findById(resourceId).orElseThrow();
						assertThat(resourceA.getVersion()).isEqualTo(0L);

						readyLatch.countDown();
						try {
							startLatch.await(); // 두 트랜잭션 모두 준비될 때까지 대기
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}

						resourceA.markScalingUp(10);
						streamingResourceRepository.saveAndFlush(resourceA);
						return null;
					});
					txASuccess.set(true);
				} catch (Exception e) {
					txAException.set(e);
				}
			});

			// 트랜잭션 B: 동일 리소스 로드 후 상태 변경
			executor.submit(() -> {
				try {
					transactionTemplate.execute(status -> {
						StreamingResource resourceB = streamingResourceRepository.findById(resourceId).orElseThrow();
						assertThat(resourceB.getVersion()).isEqualTo(0L);

						readyLatch.countDown();
						try {
							startLatch.await(); // 두 트랜잭션 모두 준비될 때까지 대기
							Thread.sleep(50); // 트랜잭션 A가 먼저 커밋되도록 약간의 지연
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}

						resourceB.markScalingDown();
						streamingResourceRepository.saveAndFlush(resourceB);
						return null;
					});
					txBSuccess.set(true);
				} catch (Exception e) {
					txBException.set(e);
				}
			});

			// 두 트랜잭션 모두 읽기 완료까지 대기
			readyLatch.await();
			// 동시에 수정 시작
			startLatch.countDown();

			executor.shutdown();
			while (!executor.isTerminated()) {
				Thread.sleep(10);
			}

			// then: 하나는 성공, 하나는 실패
			boolean exactlyOneSuccess = txASuccess.get() ^ txBSuccess.get();
			assertThat(exactlyOneSuccess)
				.as("정확히 하나의 트랜잭션만 성공해야 합니다")
				.isTrue();

			// 실패한 트랜잭션은 ObjectOptimisticLockingFailureException
			Exception failedException = txASuccess.get() ? txBException.get() : txAException.get();
			assertThat(failedException)
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
		}
	}

	@Nested
	@DisplayName("롤백 기능 테스트")
	class RollbackTest {

		@Test
		@DisplayName("markScalingUp 후 rollbackScaling 호출 시 이전 상태로 정확히 복원된다")
		void rollbackRestoresPreviousState() {
			// given
			Long resourceId = transactionTemplate.execute(status -> {
				Survey survey = createAndSaveSurvey("Rollback Test Survey");
				StreamingResource resource = createResource(survey);
				resource.assignApplication("app-rollback");
				resource.assignStreamGroup("sg-rollback");
				// READY → ACTIVE 전환 (capacity = 10)
				resource.markScalingUp(10);
				resource.markActive();
				return streamingResourceRepository.save(resource).getId();
			});

			// ACTIVE 상태 확인
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				assertThat(resource.getStatus()).isEqualTo(StreamingResourceStatus.ACTIVE);
				assertThat(resource.getCurrentCapacity()).isEqualTo(10);
				return null;
			});

			// when: markScalingUp(20) 호출
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				resource.markScalingUp(20);
				streamingResourceRepository.saveAndFlush(resource);
				return null;
			});

			// SCALING_UP 상태 및 이전 상태 저장 확인
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				assertThat(resource.getStatus()).isEqualTo(StreamingResourceStatus.SCALING_UP);
				assertThat(resource.getCurrentCapacity()).isEqualTo(20);
				assertThat(resource.getPreviousStatus()).isEqualTo(StreamingResourceStatus.ACTIVE);
				assertThat(resource.getPreviousCapacity()).isEqualTo(10);
				return null;
			});

			// when: rollbackScaling 호출
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				resource.rollbackScaling();
				streamingResourceRepository.saveAndFlush(resource);
				return null;
			});

			// then: 이전 상태로 복원 확인
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				assertThat(resource.getStatus()).isEqualTo(StreamingResourceStatus.ACTIVE);
				assertThat(resource.getCurrentCapacity()).isEqualTo(10);
				assertThat(resource.getPreviousStatus()).isNull();
				assertThat(resource.getPreviousCapacity()).isNull();
				return null;
			});
		}

		@Test
		@DisplayName("markScalingDown 후 rollbackScaling 호출 시 이전 상태로 정확히 복원된다")
		void rollbackAfterScalingDownRestoresPreviousState() {
			// given: TESTING 상태 (capacity=1) 리소스
			Long resourceId = transactionTemplate.execute(status -> {
				Survey survey = createAndSaveSurvey("ScaleDown Rollback Survey");
				StreamingResource resource = createResource(survey);
				resource.assignApplication("app-scaledown");
				resource.assignStreamGroup("sg-scaledown");
				resource.confirmStartTest(); // TESTING 상태, capacity=1
				return streamingResourceRepository.save(resource).getId();
			});

			// when: markScalingDown 호출
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				assertThat(resource.getStatus()).isEqualTo(StreamingResourceStatus.TESTING);
				assertThat(resource.getCurrentCapacity()).isEqualTo(1);

				resource.markScalingDown();
				streamingResourceRepository.saveAndFlush(resource);
				return null;
			});

			// SCALING_DOWN 상태 확인
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				assertThat(resource.getStatus()).isEqualTo(StreamingResourceStatus.SCALING_DOWN);
				assertThat(resource.getCurrentCapacity()).isEqualTo(0);
				assertThat(resource.getPreviousStatus()).isEqualTo(StreamingResourceStatus.TESTING);
				assertThat(resource.getPreviousCapacity()).isEqualTo(1);
				return null;
			});

			// when: rollbackScaling 호출
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				resource.rollbackScaling();
				streamingResourceRepository.saveAndFlush(resource);
				return null;
			});

			// then: TESTING 상태로 복원
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				assertThat(resource.getStatus()).isEqualTo(StreamingResourceStatus.TESTING);
				assertThat(resource.getCurrentCapacity()).isEqualTo(1);
				assertThat(resource.getPreviousStatus()).isNull();
				assertThat(resource.getPreviousCapacity()).isNull();
				return null;
			});
		}

		@Test
		@DisplayName("이전 상태 없이 rollbackScaling 호출 시 READY 상태로 복원된다")
		void rollbackWithoutPreviousStateRestoresToReady() {
			// given: 이전 상태 없이 SCALING_UP으로 직접 설정된 리소스 시뮬레이션
			Long resourceId = transactionTemplate.execute(status -> {
				Survey survey = createAndSaveSurvey("No Previous State Survey");
				StreamingResource resource = createResource(survey);
				resource.assignApplication("app-no-prev");
				resource.assignStreamGroup("sg-no-prev");
				// READY 상태에서 시작 (previousStatus/previousCapacity는 null)
				return streamingResourceRepository.save(resource).getId();
			});

			// when: READY에서 markScalingUp 후 rollback
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				assertThat(resource.getStatus()).isEqualTo(StreamingResourceStatus.READY);
				assertThat(resource.getCurrentCapacity()).isEqualTo(0);
				assertThat(resource.getPreviousStatus()).isNull();

				resource.markScalingUp(5);
				streamingResourceRepository.saveAndFlush(resource);
				return null;
			});

			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				assertThat(resource.getPreviousStatus()).isEqualTo(StreamingResourceStatus.READY);
				assertThat(resource.getPreviousCapacity()).isEqualTo(0);

				resource.rollbackScaling();
				streamingResourceRepository.saveAndFlush(resource);
				return null;
			});

			// then: READY, capacity=0으로 복원
			transactionTemplate.execute(status -> {
				StreamingResource resource = streamingResourceRepository.findById(resourceId).orElseThrow();
				assertThat(resource.getStatus()).isEqualTo(StreamingResourceStatus.READY);
				assertThat(resource.getCurrentCapacity()).isEqualTo(0);
				return null;
			});
		}
	}

	// === Helper Methods ===

	private Survey createAndSaveSurvey(String name) {
		return surveyRepository.save(Survey.builder()
			.game(testGame)
			.name(name)
			.build());
	}

	private StreamingResource createResource(Survey survey) {
		return StreamingResource.builder()
			.survey(survey)
			.build(testBuild)
			.instanceType("gen4n.large")
			.maxCapacity(100)
			.build();
	}
}
