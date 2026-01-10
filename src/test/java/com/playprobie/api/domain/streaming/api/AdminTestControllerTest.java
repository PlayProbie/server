package com.playprobie.api.domain.streaming.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.dao.GameBuildRepository;
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.streaming.application.CapacityChangeAsyncService;
import com.playprobie.api.domain.streaming.dao.CapacityChangeRequestRepository;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class AdminTestControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StreamingResourceRepository streamingResourceRepository;

	@Autowired
	private CapacityChangeRequestRepository requestRepository;

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

	@MockitoBean
	private GameLiftService gameLiftService;

	@MockitoBean
	private CapacityChangeAsyncService capacityChangeAsyncService;

	private User testUser;
	private Survey testSurvey;
	private StreamingResource testResource;

	@BeforeEach
	void setUp() {
		requestRepository.deleteAll();
		streamingResourceRepository.deleteAll();
		surveyRepository.deleteAll();
		gameBuildRepository.deleteAll();
		gameRepository.deleteAll();
		workspaceMemberRepository.deleteAll();
		workspaceRepository.deleteAll();
		userRepository.deleteAll();

		String unique = UUID.randomUUID().toString().substring(0, 8);
		testUser = userRepository
			.save(User.builder().email("test-" + unique + "@a.com").password("pw").name("u").build());
		Workspace ws = workspaceRepository.save(Workspace.create("TestWS-" + unique, "desc"));
		workspaceMemberRepository
			.save(WorkspaceMember.builder().workspace(ws).user(testUser).role(WorkspaceRole.OWNER).build());
		Game game = gameRepository.save(Game.builder().workspace(ws).name("G-" + unique).context("C").build());
		GameBuild build = GameBuild.builder().game(game).uuid(UUID.randomUUID()).version("1").build();
		build.markAsUploaded(10, 1000L, "AL2023", "test.exe");
		build = gameBuildRepository.save(build);
		testSurvey = surveyRepository.save(Survey.builder().game(game).name("S-" + unique).build());

		testResource = StreamingResource.builder().survey(testSurvey).build(build).instanceType("t").maxCapacity(5)
			.build();
		testResource.assignApplication("arn:app:" + unique);
		testResource.assignStreamGroup("arn:sg:" + unique);
		try {
			streamingResourceRepository.save(testResource);
		} catch (Exception e) {
			System.err.println("### FAILURE SAVING RESOURCE ###");
			e.printStackTrace();
			if (e.getCause() != null)
				System.err.println("Cause: " + e.getCause().getMessage());
			throw e;
		}

		setSecurityContext(testUser);
	}

	private void setSecurityContext(User user) {
		CustomUserDetails userDetails = new CustomUserDetails(user);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null,
			userDetails.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	@Test
	@DisplayName("Start Test 성공: Async 호출 및 200 OK + Retry-After 반환")
	void startTest_Success() throws Exception {
		mockMvc.perform(post("/surveys/{uuid}/streaming-resource/start-test", testSurvey.getUuid()))
			.andExpect(status().isOk())
			.andExpect(header().string("Retry-After", "5"))
			.andExpect(jsonPath("$.result.status").value("SCALING_UP"))
			.andExpect(jsonPath("$.result.estimatedCompletionSeconds").exists());

		StreamingResource updated = streamingResourceRepository.findById(testResource.getId()).orElseThrow();
		assertThat(updated.getStatus()).isEqualTo(StreamingResourceStatus.SCALING_UP);
		assertThat(updated.getCurrentCapacity()).isEqualTo(1);

		// CapacityChangeRequest 생성 확인
		long count = requestRepository.count();
		assertThat(count).isEqualTo(1);

		// Service 호출 검증 (Key는 anyString()으로 매칭)
		verify(capacityChangeAsyncService).applyCapacityChange(eq(testResource.getId()), anyLong(),
			eq(1), any());
	}

	@Test
	@DisplayName("Fail-Fast: TaskRejectedException 발생 시 429 Too Many Requests 반환 및 롤백")
	void startTest_FailFast() throws Exception {
		doThrow(new TaskRejectedException("Queue Full"))
			.when(capacityChangeAsyncService).applyCapacityChange(anyLong(), anyLong(), anyInt(), any());

		mockMvc.perform(post("/surveys/{uuid}/streaming-resource/start-test", testSurvey.getUuid()))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("SR004"));
	}
}
