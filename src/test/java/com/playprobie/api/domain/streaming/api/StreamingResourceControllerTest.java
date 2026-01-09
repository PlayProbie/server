package com.playprobie.api.domain.streaming.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
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
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playprobie.api.domain.game.dao.GameBuildRepository;
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.domain.streaming.domain.StreamingResourceStatus;
import com.playprobie.api.domain.streaming.dto.CreateStreamingResourceRequest;
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

import software.amazon.awssdk.services.gameliftstreams.model.CreateApplicationResponse;
import software.amazon.awssdk.services.gameliftstreams.model.CreateStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.GetStreamGroupResponse;
import software.amazon.awssdk.services.gameliftstreams.model.StreamGroupStatus;

/**
 * StreamingResourceController 통합 테스트.
 *
 * <p>
 * 전체 Spring Context를 로드하고 Controller-Service-Repository 흐름을 검증합니다.
 * 외부 AWS 연동(GameLiftService)만 @MockBean으로 처리합니다.
 *
 * <p>
 * NOTE: @Async 메서드(StreamingProvisioner)를 테스트하기 위해 SyncTaskExecutor를 사용합니다.
 * 이를 통해 비동기 로직이 테스트 스레드(메인 스레드)에서 동기적으로 실행되어 트랜잭션을 공유하게 됩니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("local")
@Import(StreamingResourceControllerTest.TestAsyncConfig.class)
@org.springframework.test.context.TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class StreamingResourceControllerTest {

	@TestConfiguration
	public static class TestAsyncConfig {
		@Bean(name = "taskExecutor")
		@org.springframework.context.annotation.Primary
		public Executor taskExecutor() {
			return new SyncTaskExecutor();
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

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

	// === Test Fixtures ===
	private User testUser;
	private Workspace testWorkspace;
	private Game testGame;
	private GameBuild testBuild;
	private Survey testSurvey;

	@BeforeEach
	void setUp() {
		// 1. User 생성 및 저장
		testUser = User.builder()
			.email("test@example.com")
			.password("password123")
			.name("Test User")
			.build();
		userRepository.save(testUser);

		// 2. Workspace 생성 및 저장
		testWorkspace = Workspace.create("Test Workspace", "테스트 워크스페이스");
		workspaceRepository.save(testWorkspace);

		// 3. WorkspaceMember 생성 (OWNER 권한)
		WorkspaceMember member = WorkspaceMember.builder()
			.workspace(testWorkspace)
			.user(testUser)
			.role(WorkspaceRole.OWNER)
			.build();
		workspaceMemberRepository.save(member);

		// 4. Game 생성 및 저장
		testGame = Game.builder()
			.workspace(testWorkspace)
			.name("Test Game")
			.context("테스트용 게임")
			.build();
		gameRepository.save(testGame);

		// 5. GameBuild 생성 및 저장 (UPLOADED 상태, Windows OS)
		testBuild = GameBuild.builder()
			.game(testGame)
			.uuid(UUID.randomUUID())
			.version("1.0.0")
			.build();
		testBuild.markAsUploaded(10, 1024L, "WINDOWS", "/game/test.exe");
		gameBuildRepository.save(testBuild);

		// 6. Survey 생성 및 저장
		testSurvey = Survey.builder()
			.game(testGame)
			.name("Test Survey")
			.build();
		surveyRepository.save(testSurvey);

		// 7. SecurityContext에 인증 토큰 주입
		// @AuthenticationPrincipal(expression = "user")와 호환되도록 CustomUserDetails 사용
		CustomUserDetails userDetails = new CustomUserDetails(testUser);
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null,
			userDetails.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	@Test
	@DisplayName("POST /surveys/{surveyId}/streaming-resource - 스트리밍 리소스 생성 시 202 응답과 실제 프로비저닝 로직 수행")
	void createResource_ShouldReturn202AndExecuteProvisioning() throws Exception {
		/*
		 * 이 테스트는 `SyncTaskExecutor`를 사용하여 비동기 로직을 동기적으로 수행합니다.
		 * 따라서 실제 환경(202 Accepted, CREATING)과 달리 응답 시점에 이미 리소스 할당이
		 * 완료되어 상태가 `READY`로 반환됩니다.
		 */

		// given
		CreateStreamingResourceRequest request = new CreateStreamingResourceRequest(
			testBuild.getUuid(),
			"gen4n_win2022",
			10);

		String requestBody = objectMapper.writeValueAsString(request);

		// Mock GameLiftService behaviors needed for StreamingProvisioner
		given(gameLiftService.createApplication(anyString(), anyString(), anyString(), anyString()))
			.willReturn(CreateApplicationResponse.builder().arn("arn:aws:gamelift:app-123").build());

		given(gameLiftService.createStreamGroup(anyString(), anyString()))
			.willReturn(CreateStreamGroupResponse.builder().arn("arn:aws:gamelift:sg-123").build());

		// waitForStreamGroupStable Loop: Return ACTIVE to exit loop immediately
		given(gameLiftService.getStreamGroupStatus(anyString()))
			.willReturn(GetStreamGroupResponse.builder().status(StreamGroupStatus.ACTIVE).build());

		// when
		mockMvc.perform(post("/surveys/{surveyId}/streaming-resource", testSurvey.getUuid())
			.contentType(MediaType.APPLICATION_JSON)
			.content(requestBody))
			.andExpect(status().isAccepted())
			.andExpect(header().exists("Location"))
			.andExpect(jsonPath("$.result.uuid").exists())
			.andExpect(jsonPath("$.result.status").value("READY"));

		// then - DB에 실제로 리소스가 생성되고 상태가 업데이트 되었는지 검증
		// SyncTaskExecutor로 인해 프로비저닝 로직이 동기적으로 실행되었으므로,
		// 최종 상태는 CREATING -> PROVISIONING -> READY 상태여야 함
		Optional<StreamingResource> savedResource = streamingResourceRepository.findBySurveyId(testSurvey.getId());
		assertThat(savedResource).isPresent();

		// StreamingProvisioner가 성공적으로 완료되면 상태는 READY
		assertThat(savedResource.get().getStatus()).isEqualTo(StreamingResourceStatus.READY);
		assertThat(savedResource.get().getAwsApplicationId()).isEqualTo("arn:aws:gamelift:app-123");
		assertThat(savedResource.get().getAwsStreamGroupId()).isEqualTo("arn:aws:gamelift:sg-123");
		assertThat(savedResource.get().getMaxCapacity()).isEqualTo(10);

		// GameLiftService 메서드 호출 검증: 각 메서드가 정확히 1회 호출되었는지 확인
		verify(gameLiftService, times(1)).createApplication(anyString(), anyString(), anyString(), anyString());
		verify(gameLiftService, times(1)).createStreamGroup(anyString(), anyString());
		verify(gameLiftService, times(1)).getStreamGroupStatus(anyString());
	}

	@Test
	@DisplayName("GET /surveys/{surveyId}/streaming-resource - 스트리밍 리소스 조회 시 200 응답")
	void getResource_ShouldReturn200WithResourceData() throws Exception {
		// given - 스트리밍 리소스 미리 생성
		StreamingResource resource = StreamingResource.builder()
			.survey(testSurvey)
			.build(testBuild)
			.instanceType("gen4n_win2022")
			.maxCapacity(5)
			.build();
		streamingResourceRepository.save(resource);

		// when & then
		mockMvc.perform(get("/surveys/{surveyId}/streaming-resource", testSurvey.getUuid()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.uuid").value(resource.getUuid().toString()))
			.andExpect(jsonPath("$.result.status").value("CREATING"))
			.andExpect(jsonPath("$.result.max_capacity").value(5))
			.andExpect(jsonPath("$.result.instance_type").value("gen4n_win2022"));
	}

	@Test
	@DisplayName("DELETE /surveys/{surveyId}/streaming-resource - 스트리밍 리소스 삭제 시 204 응답")
	void deleteResource_ShouldReturn204AndRemoveFromDB() throws Exception {
		// given - 스트리밍 리소스 미리 생성
		StreamingResource resource = StreamingResource.builder()
			.survey(testSurvey)
			.build(testBuild)
			.instanceType("gen4n_win2022")
			.maxCapacity(5)
			.build();
		// Mock AWS resource IDs for deletion logic
		resource.assignApplication("arn:aws:gamelift:app-123");
		resource.assignStreamGroup("arn:aws:gamelift:sg-123");
		streamingResourceRepository.save(resource);

		Long resourceId = resource.getId();

		// when
		mockMvc.perform(delete("/surveys/{surveyId}/streaming-resource", testSurvey.getUuid()))
			.andExpect(status().isNoContent());

		// then - DB에서 실제로 삭제되었는지 검증
		Optional<StreamingResource> deletedResource = streamingResourceRepository.findById(resourceId);
		assertThat(deletedResource).isEmpty();

		// GameLiftService의 삭제 메서드들도 반드시 호출되어야 함을 검증
		verify(gameLiftService, times(1)).deleteStreamGroup("arn:aws:gamelift:sg-123");
		verify(gameLiftService, times(1)).deleteApplication("arn:aws:gamelift:app-123");
	}
}
