package com.playprobie.api.domain.survey.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playprobie.api.domain.game.dao.GameBuildRepository;
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.domain.SurveyStatus;
import com.playprobie.api.domain.survey.dto.request.UpdateSurveyStatusRequest;
import com.playprobie.api.domain.user.dao.UserRepository;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.dao.WorkspaceMemberRepository;
import com.playprobie.api.domain.workspace.dao.WorkspaceRepository;
import com.playprobie.api.domain.workspace.domain.Workspace;
import com.playprobie.api.domain.workspace.domain.WorkspaceMember;
import com.playprobie.api.domain.workspace.domain.WorkspaceRole;
import com.playprobie.api.global.security.CustomUserDetails;
import com.playprobie.api.infra.gamelift.GameLiftService;

import jakarta.persistence.EntityManager;

/**
 * SurveyController의 설문 상태 변경 API 통합 테스트.
 *
 * <p>
 * Safe Method 패턴 적용(리소스 부재 시 롤백 방지) 검증을 목표로 함.
 * Mock 대신 Fake 객체를 사용하여 상태 검증(State Verification)을 수행합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(SurveyStatusIntegrationTest.TestConfig.class)
@org.springframework.test.context.TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class SurveyStatusIntegrationTest {

	@TestConfiguration
	static class TestConfig {
		@Bean(name = "taskExecutor")
		@Primary
		public Executor taskExecutor() {
			return new SyncTaskExecutor();
		}

		@Bean
		@Primary
		public GameLiftService gameLiftService() {
			return new FakeGameLiftService();
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EntityManager em;

	// Repositories
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private WorkspaceRepository workspaceRepository;
	@Autowired
	private WorkspaceMemberRepository workspaceMemberRepository;
	@Autowired
	private GameRepository gameRepository;
	@Autowired
	private SurveyRepository surveyRepository;
	@Autowired
	private StreamingResourceRepository streamingResourceRepository;
	@Autowired
	private GameBuildRepository gameBuildRepository;

	// Fake (상태 검증용)
	@Autowired
	private GameLiftService gameLiftService;

	private FakeGameLiftService fakeGameLiftService() {
		return (FakeGameLiftService)gameLiftService;
	}

	private User testUser;
	private Workspace testWorkspace;
	private Game testGame;

	@BeforeEach
	void setUp() {
		// Fake 상태 초기화
		fakeGameLiftService().reset();

		testUser = userRepository.save(User.builder()
			.email("tester@example.com")
			.password("password")
			.name("Tester")
			.build());

		testWorkspace = workspaceRepository.save(Workspace.create("TestWS", "Description"));

		workspaceMemberRepository.save(WorkspaceMember.builder()
			.workspace(testWorkspace)
			.user(testUser)
			.role(WorkspaceRole.OWNER)
			.build());

		testGame = gameRepository.save(Game.builder()
			.workspace(testWorkspace)
			.name("TestGame")
			.context("GameContext")
			.build());

		// Security Context Setup
		CustomUserDetails userDetails = new CustomUserDetails(testUser);
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
	}

	// --- A. 리소스가 없는 경우 ---

	@Test
	@DisplayName("1. 리소스가 없는 설문을 ACTIVE로 변경하면 성공하고 롤백이 발생하지 않는다")
	void updateStatus_ToActive_NoResource_Success() throws Exception {
		// Given
		Survey survey = createAndSaveSurvey(false);
		UpdateSurveyStatusRequest request = new UpdateSurveyStatusRequest("ACTIVE");

		// When
		mockMvc.perform(patch("/surveys/{uuid}/status", survey.getUuid())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.status").value("ACTIVE"))
			.andExpect(jsonPath("$.result.streaming_resource").value((Object)null));

		// Then: DB 상태 검증
		flushAndClear();
		Survey updatedSurvey = surveyRepository.findById(survey.getId()).orElseThrow();
		assertThat(updatedSurvey.getStatus()).isEqualTo(SurveyStatus.ACTIVE);

		// Fake 상태 검증: AWS 호출 없음
		assertThat(fakeGameLiftService().getCapacityUpdates()).isEmpty();
	}

	@Test
	@DisplayName("2. 리소스가 없는 설문을 CLOSED로 변경하면 deleteStreamGroup 호출 없이 성공한다")
	void updateStatus_ToClosed_NoResource_Success() throws Exception {
		// Given
		Survey survey = createAndSaveSurvey(false);
		UpdateSurveyStatusRequest request = new UpdateSurveyStatusRequest("CLOSED");

		// When
		mockMvc.perform(patch("/surveys/{uuid}/status", survey.getUuid())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.status").value("CLOSED"));

		// Then: DB 상태 검증
		flushAndClear();
		Survey updatedSurvey = surveyRepository.findById(survey.getId()).orElseThrow();
		assertThat(updatedSurvey.getStatus()).isEqualTo(SurveyStatus.CLOSED);
		assertThat(streamingResourceRepository.findBySurveyId(survey.getId())).isEmpty();

		// Fake 상태 검증: 삭제 호출 없음
		assertThat(fakeGameLiftService().getDeletedStreamGroups()).isEmpty();
		assertThat(fakeGameLiftService().getDeletedApplications()).isEmpty();
	}

	// --- B. 리소스가 있는 경우 ---

	@Test
	@DisplayName("3. 리소스가 있는 설문을 ACTIVE로 변경하면 용량 확장 요청이 생성된다")
	void updateStatus_ToActive_WithResource_Success() throws Exception {
		// Given
		Survey survey = createAndSaveSurvey(true);
		UpdateSurveyStatusRequest request = new UpdateSurveyStatusRequest("ACTIVE");

		// When & Then: API 응답 검증 (Black-box)
		mockMvc.perform(patch("/surveys/{uuid}/status", survey.getUuid())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.status").value("ACTIVE"))
			.andExpect(jsonPath("$.result.streaming_resource").exists())
			.andExpect(jsonPath("$.result.streaming_resource.status").value("SCALING_UP"));

		// Fake 상태 검증: updateStreamGroupCapacity 호출됨
		assertThat(fakeGameLiftService().getCapacityUpdates()).hasSize(1);
		assertThat(fakeGameLiftService().getCapacityUpdates().get(0).targetCapacity()).isEqualTo(10);
	}

	@Test
	@DisplayName("4. 리소스가 있는 설문을 CLOSED로 변경하면 리소스가 삭제된다")
	void updateStatus_ToClosed_WithResource_Success() throws Exception {
		// Given
		Survey survey = createAndSaveSurvey(true);
		UpdateSurveyStatusRequest request = new UpdateSurveyStatusRequest("CLOSED");

		// When
		mockMvc.perform(patch("/surveys/{uuid}/status", survey.getUuid())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.status").value("CLOSED"));

		// Then: DB 상태 검증
		flushAndClear();
		Survey updatedSurvey = surveyRepository.findById(survey.getId()).orElseThrow();
		assertThat(updatedSurvey.getStatus()).isEqualTo(SurveyStatus.CLOSED);
		assertThat(streamingResourceRepository.findBySurveyId(survey.getId())).isEmpty();

		// Fake 상태 검증: 삭제 호출됨
		assertThat(fakeGameLiftService().getDeletedStreamGroups()).hasSize(1);
		assertThat(fakeGameLiftService().getDeletedApplications()).hasSize(1);
	}

	// --- C. 예외 케이스 ---

	@Test
	@DisplayName("5. 존재하지 않는 설문 UUID로 요청하면 404를 반환한다")
	void updateStatus_SurveyNotFound() throws Exception {
		// Given
		UUID randomUuid = UUID.randomUUID();
		UpdateSurveyStatusRequest request = new UpdateSurveyStatusRequest("ACTIVE");

		// When & Then
		mockMvc.perform(patch("/surveys/{uuid}/status", randomUuid)
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isNotFound());
	}

	// --- Helpers ---

	private Survey createAndSaveSurvey(boolean hasResource) {
		Survey survey = surveyRepository.save(Survey.builder()
			.game(testGame)
			.name(hasResource ? "Resource Survey" : "No Resource Survey")
			.build());

		if (hasResource) {
			GameBuild build = gameBuildRepository.save(GameBuild.builder()
				.game(testGame)
				.uuid(UUID.randomUUID())
				.version("1.0.0")
				.build());
			build.markAsUploaded(100, 1024L, "WINDOWS", "test.zip");
			gameBuildRepository.save(build);

			StreamingResource resource = StreamingResource.builder()
				.survey(survey)
				.build(build)
				.instanceType("gen4n.large")
				.maxCapacity(10)
				.build();

			String uniqueId = UUID.randomUUID().toString();
			resource.assignApplication("arn:aws:gamelift:app-" + uniqueId);
			resource.assignStreamGroup("arn:aws:gamelift:sg-" + uniqueId);

			streamingResourceRepository.save(resource);
		}

		return survey;
	}

	private void flushAndClear() {
		em.flush();
		em.clear();
	}
}
