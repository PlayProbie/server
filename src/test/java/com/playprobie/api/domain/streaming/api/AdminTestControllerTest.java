package com.playprobie.api.domain.streaming.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.dao.GameBuildRepository;
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.domain.streaming.domain.StreamingResourceStatus;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.user.dao.UserRepository;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.dao.WorkspaceMemberRepository;
import com.playprobie.api.domain.workspace.dao.WorkspaceRepository;
import com.playprobie.api.domain.workspace.domain.Workspace;
import com.playprobie.api.domain.workspace.domain.WorkspaceMember;
import com.playprobie.api.domain.workspace.domain.WorkspaceRole;
import com.playprobie.api.global.security.CustomUserDetails;
import com.playprobie.api.infra.gamelift.GameLiftService;

import software.amazon.awssdk.services.gameliftstreams.model.GetStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.StreamGroupStatus;

/**
 * AdminTestController 통합 테스트.
 *
 * <p>
 * 전체 Spring Context를 로드하고 Controller-Service-Repository 흐름을 검증합니다.
 * 외부 AWS 연동(GameLiftService)만 @MockBean으로 처리합니다.
 *
 * <p>
 * NOTE: @Async 메서드를 테스트하기 위해 SyncTaskExecutor를 사용합니다.
 * 이를 통해 비동기 로직이 테스트 스레드(메인 스레드)에서 동기적으로 실행되어 트랜잭션을 공유하게 됩니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("local")
@Import(AdminTestControllerTest.TestAsyncConfig.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class AdminTestControllerTest {

	@TestConfiguration
	public static class TestAsyncConfig {
		@Bean(name = "taskExecutor")
		@Primary
		public Executor taskExecutor() {
			return new SyncTaskExecutor();
		}
	}

	// === Infrastructure ===
	@Autowired
	private MockMvc mockMvc;

	// === Repositories ===
	@Autowired
	private UserRepository userRepository;

	@Autowired
	private WorkspaceRepository workspaceRepository;

	@Autowired
	private WorkspaceMemberRepository workspaceMemberRepository;

	@Autowired
	private GameRepository gameRepository;

	@Autowired
	private GameBuildRepository gameBuildRepository;

	@Autowired
	private SurveyRepository surveyRepository;

	@Autowired
	private StreamingResourceRepository streamingResourceRepository;

	// === Mock External Services ===
	@MockitoBean
	private GameLiftService gameLiftService;

	// === Test Constants ===
	private static final String TEST_STREAM_GROUP_ARN = "arn:aws:gamelift:us-east-1:123456789012:streamgroup/sg-test";
	private static final String TEST_APPLICATION_ARN = "arn:aws:gamelift:us-east-1:123456789012:application/app-test";

	// === Test Fixtures ===
	private User testUser;
	private User nonMemberUser; // 워크스페이스 멤버가 아닌 사용자
	private Workspace testWorkspace;
	private Game testGame;
	private GameBuild testBuild;
	private Survey testSurvey;
	private StreamingResource testResource;

	@BeforeEach
	void setUp() {
		// 1. 사용자 생성 (UUID 기반 이메일로 중복 방지)
		String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
		testUser = createAndSaveUser("owner-" + uniqueSuffix + "@example.com", "Owner User");
		nonMemberUser = createAndSaveUser("nonmember-" + uniqueSuffix + "@example.com", "Non-Member User");

		// 2. Workspace 생성
		testWorkspace = createAndSaveWorkspace("Test Workspace");

		// 3. WorkspaceMember 생성 - testUser만 OWNER, nonMemberUser는 멤버로 추가하지 않음
		createAndSaveWorkspaceMember(testWorkspace, testUser, WorkspaceRole.OWNER);

		// 4. Game 생성
		testGame = createAndSaveGame(testWorkspace);

		// 5. GameBuild 생성
		testBuild = createAndSaveGameBuild(testGame);

		// 6. Survey 생성
		testSurvey = createAndSaveSurvey(testGame);

		// 7. StreamingResource 생성 (READY 상태, AWS ARN 할당됨)
		testResource = createAndSaveStreamingResource(testSurvey, testBuild);

		// 8. SecurityContext에 기본 사용자 설정
		setSecurityContext(testUser);

		// 9. Mock 리셋 (이전 테스트의 호출 이력 삭제)
		reset(gameLiftService);
	}

	// ========== Helper Methods ==========

	private User createAndSaveUser(String email, String name) {
		User user = User.builder()
			.email(email)
			.password("password123")
			.name(name)
			.build();
		return userRepository.save(user);
	}

	private Workspace createAndSaveWorkspace(String name) {
		Workspace workspace = Workspace.create(name, "테스트 워크스페이스");
		return workspaceRepository.save(workspace);
	}

	private void createAndSaveWorkspaceMember(Workspace workspace, User user, WorkspaceRole role) {
		WorkspaceMember member = WorkspaceMember.builder()
			.workspace(workspace)
			.user(user)
			.role(role)
			.build();
		workspaceMemberRepository.save(member);
	}

	private Game createAndSaveGame(Workspace workspace) {
		Game game = Game.builder()
			.workspace(workspace)
			.name("Test Game")
			.context("테스트용 게임")
			.build();
		return gameRepository.save(game);
	}

	private GameBuild createAndSaveGameBuild(Game game) {
		GameBuild build = GameBuild.builder()
			.game(game)
			.uuid(UUID.randomUUID())
			.version("1.0.0")
			.build();
		build.markAsUploaded(10, 1024L, "WINDOWS", "/game/test.exe");
		return gameBuildRepository.save(build);
	}

	private Survey createAndSaveSurvey(Game game) {
		Survey survey = Survey.builder()
			.game(game)
			.name("Test Survey")
			.build();
		return surveyRepository.save(survey);
	}

	private StreamingResource createAndSaveStreamingResource(Survey survey, GameBuild build) {
		StreamingResource resource = StreamingResource.builder()
			.survey(survey)
			.build(build)
			.instanceType("gen4n_win2022")
			.maxCapacity(5)
			.build();

		// READY 상태로 설정 (AWS ARN 할당)
		resource.assignApplication(TEST_APPLICATION_ARN);
		resource.assignStreamGroup(TEST_STREAM_GROUP_ARN);

		return streamingResourceRepository.save(resource);
	}

	private void setSecurityContext(User user) {
		CustomUserDetails userDetails = new CustomUserDetails(user);
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			userDetails, null, userDetails.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	// ========== Test Cases ==========

	@Nested
	@DisplayName("POST /surveys/{surveyId}/streaming-resource/start-test")
	class StartTestTests {

		@Test
		@DisplayName("관리자 테스트 시작 - 성공: DB 상태가 TESTING으로 변경되고 AWS 용량이 1로 업데이트된다")
		void startTest_Success() throws Exception {
			// when
			mockMvc.perform(post("/surveys/{surveyId}/streaming-resource/start-test", testSurvey.getUuid()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.status").value("TESTING"))
				.andExpect(jsonPath("$.result.current_capacity").value(1))
				.andExpect(jsonPath("$.result.message").value("인스턴스 준비 중입니다."));

			// then - DB 상태 검증 (트랜잭션 내에서 조회)
			Optional<StreamingResource> savedResource = streamingResourceRepository.findById(testResource.getId());
			assertThat(savedResource).isPresent();
			assertThat(savedResource.get().getStatus()).isEqualTo(StreamingResourceStatus.TESTING);
			assertThat(savedResource.get().getCurrentCapacity()).isEqualTo(1);

			// then - GameLiftService 호출 검증 (정확한 파라미터)
			verify(gameLiftService, times(1)).updateStreamGroupCapacity(eq(TEST_STREAM_GROUP_ARN), eq(1));
		}

		@Test
		@DisplayName("관리자 테스트 시작 - 실패: 워크스페이스 멤버가 아닌 사용자는 403 Forbidden 응답을 받는다")
		void startTest_Forbidden_WhenUserIsNotMember() throws Exception {
			// given - 멤버가 아닌 사용자로 SecurityContext 변경
			setSecurityContext(nonMemberUser);

			// when & then - 멤버가 아니면 WORKSPACE_MEMBER_NOT_FOUND (403 Forbidden)
			mockMvc.perform(post("/surveys/{surveyId}/streaming-resource/start-test", testSurvey.getUuid()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("W002")); // WORKSPACE_MEMBER_NOT_FOUND

			// GameLiftService가 호출되지 않아야 함
			verify(gameLiftService, never()).updateStreamGroupCapacity(anyString(), anyInt());
		}

		@Test
		@DisplayName("관리자 테스트 시작 - 실패: 존재하지 않는 surveyId는 404 Not Found 응답을 받는다")
		void startTest_NotFound_WhenSurveyNotExists() throws Exception {
			// given
			UUID nonExistentSurveyId = UUID.randomUUID();

			// when & then
			mockMvc.perform(post("/surveys/{surveyId}/streaming-resource/start-test", nonExistentSurveyId))
				.andExpect(status().isNotFound());

			// GameLiftService가 호출되지 않아야 함
			verify(gameLiftService, never()).updateStreamGroupCapacity(anyString(), anyInt());
		}

		@Test
		@DisplayName("관리자 테스트 시작 - 실패: StreamingResource가 없는 Survey는 404 Not Found 응답을 받는다")
		void startTest_NotFound_WhenResourceNotExists() throws Exception {
			// given - 리소스가 없는 새 Survey 생성
			Survey surveyWithoutResource = Survey.builder()
				.game(testGame)
				.name("Survey Without Resource")
				.build();
			surveyRepository.save(surveyWithoutResource);

			// when & then
			mockMvc.perform(
				post("/surveys/{surveyId}/streaming-resource/start-test", surveyWithoutResource.getUuid()))
				.andExpect(status().isNotFound());

			// GameLiftService가 호출되지 않아야 함
			verify(gameLiftService, never()).updateStreamGroupCapacity(anyString(), anyInt());
		}

		@Test
		@DisplayName("관리자 테스트 시작 - 실패: READY 상태가 아닌 리소스(PROVISIONING)는 IllegalStateException이 발생한다")
		void startTest_ServerError_WhenResourceStatusNotReady() throws Exception {
			// given - PROVISIONING 상태의 리소스가 있는 Survey 생성
			Survey provisioningSurvey = createAndSaveSurvey(testGame);
			StreamingResource provisioningResource = StreamingResource.builder()
				.survey(provisioningSurvey)
				.build(testBuild)
				.instanceType("gen4n_win2022")
				.maxCapacity(5)
				.build();
			// assignApplication만 호출하면 PROVISIONING 상태
			provisioningResource.assignApplication(TEST_APPLICATION_ARN);
			streamingResourceRepository.save(provisioningResource);

			// when & then - PROVISIONING 상태에서는 startTest 불가 (IllegalStateException -> 500)
			mockMvc.perform(post("/surveys/{surveyId}/streaming-resource/start-test", provisioningSurvey.getUuid()))
				.andExpect(status().is5xxServerError());

			// GameLiftService가 호출되지 않아야 함
			verify(gameLiftService, never()).updateStreamGroupCapacity(anyString(), anyInt());
		}
	}

	@Nested
	@DisplayName("POST /surveys/{surveyId}/streaming-resource/stop-test")
	class StopTestTests {

		@Test
		@DisplayName("관리자 테스트 종료 - 성공: DB 상태가 READY로 복원되고 AWS 용량이 0으로 업데이트된다")
		void stopTest_Success() throws Exception {
			// given - 리소스를 TESTING 상태로 변경
			testResource.startTest();
			streamingResourceRepository.saveAndFlush(testResource);

			// when
			mockMvc.perform(post("/surveys/{surveyId}/streaming-resource/stop-test", testSurvey.getUuid()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.status").value("READY"))
				.andExpect(jsonPath("$.result.current_capacity").value(0))
				.andExpect(jsonPath("$.result.message").doesNotExist());

			// then - DB 상태 검증
			Optional<StreamingResource> savedResource = streamingResourceRepository.findById(testResource.getId());
			assertThat(savedResource).isPresent();
			assertThat(savedResource.get().getStatus()).isEqualTo(StreamingResourceStatus.READY);
			assertThat(savedResource.get().getCurrentCapacity()).isEqualTo(0);

			// then - GameLiftService 호출 검증 (정확한 파라미터)
			verify(gameLiftService, times(1)).updateStreamGroupCapacity(eq(TEST_STREAM_GROUP_ARN), eq(0));
		}
	}

	@Nested
	@DisplayName("GET /surveys/{surveyId}/streaming-resource/status")
	class GetStatusTests {

		@Test
		@DisplayName("리소스 상태 조회 - 성공: READY 상태의 리소스 정보를 반환한다")
		void getStatus_Success() throws Exception {
			// given - READY 상태
			// 서비스 로직: READY는 isTransitionalState()에서 true를 반환하므로 AWS API 호출됨
			// Mock 설정
			given(gameLiftService.getStreamGroupStatus(eq(TEST_STREAM_GROUP_ARN)))
				.willReturn(GetStreamGroupResponse.builder()
					.status(StreamGroupStatus.ACTIVE)
					.build());

			// when & then
			mockMvc.perform(get("/surveys/{surveyId}/streaming-resource/status", testSurvey.getUuid()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.status").exists())
				.andExpect(jsonPath("$.result.current_capacity").exists())
				.andExpect(jsonPath("$.result.instances_ready").exists());
		}

		@Test
		@DisplayName("리소스 상태 조회 - Transitional State(TESTING)에서 AWS API를 호출하여 상태를 동기화한다")
		void getStatus_SyncsWithAWS_WhenTransitionalState() throws Exception {
			// given - 리소스를 TESTING 상태로 변경 (Transitional State)
			testResource.startTest();
			streamingResourceRepository.saveAndFlush(testResource);

			// Mock AWS 응답 설정
			given(gameLiftService.getStreamGroupStatus(eq(TEST_STREAM_GROUP_ARN)))
				.willReturn(GetStreamGroupResponse.builder()
					.status(StreamGroupStatus.ACTIVE)
					.build());

			// when & then - TESTING은 Transitional State이므로 AWS API 호출됨
			mockMvc.perform(get("/surveys/{surveyId}/streaming-resource/status", testSurvey.getUuid()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.status").exists())
				.andExpect(jsonPath("$.result.current_capacity").exists())
				.andExpect(jsonPath("$.result.instances_ready").value(true));

			// AWS API 호출 검증 (synchronizeState에서 추가 호출 가능)
			verify(gameLiftService, atLeastOnce()).getStreamGroupStatus(eq(TEST_STREAM_GROUP_ARN));
		}
	}
}
