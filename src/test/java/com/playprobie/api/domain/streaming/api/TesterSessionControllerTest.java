package com.playprobie.api.domain.streaming.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playprobie.api.domain.game.dao.GameBuildRepository;
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.SessionStatus;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.streaming.dao.StreamingResourceRepository;
import com.playprobie.api.domain.streaming.domain.StreamingResource;
import com.playprobie.api.domain.streaming.dto.SignalRequest;
import com.playprobie.api.domain.streaming.dto.TerminateSessionRequest;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.workspace.dao.WorkspaceRepository;
import com.playprobie.api.domain.workspace.domain.Workspace;
import com.playprobie.api.infra.gamelift.GameLiftService;

import software.amazon.awssdk.services.gameliftstreams.model.StartStreamSessionResponse;

/**
 * TesterSessionController 통합 테스트.
 *
 * <p>
 * 전체 Spring Context를 로드하고 Controller-Service-Repository 흐름을 검증합니다.
 * 외부 AWS 연동(GameLiftService)만 @MockBean으로 처리합니다.
 *
 * <p>
 * NOTE: 해당 컨트롤러 경로("/surveys/**")는 SecurityConstants Whitelist에 포함되어
 * 인증 없이 접근 가능합니다.
 *
 * <p>
 * NOTE: @Async 메서드 동작을 테스트하기 위해 SyncTaskExecutor를 사용합니다.
 * 이를 통해 비동기 로직이 테스트 스레드(메인 스레드)에서 동기적으로 실행되어 트랜잭션을 공유하게 됩니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("local")
@Import(TesterSessionControllerTest.TestAsyncConfig.class)
@org.springframework.test.context.TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class TesterSessionControllerTest {

	@TestConfiguration
	public static class TestAsyncConfig {
		@Bean(name = "taskExecutor")
		@org.springframework.context.annotation.Primary
		public Executor taskExecutor() {
			return new SyncTaskExecutor();
		}
	}

	// === Test Constants (AWS Mock 응답용) ===
	private static final String MOCK_AWS_SESSION_ARN = "arn:aws:gamelift:streams:us-east-1:123456789012:streamsession/sg-test/session-mock-456";
	private static final String MOCK_SIGNAL_RESPONSE = "base64EncodedSignalAnswer";
	private static final String TEST_SIGNAL_REQUEST = "base64EncodedSignalRequest";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	// === Repositories ===
	@Autowired
	private WorkspaceRepository workspaceRepository;

	@Autowired
	private GameRepository gameRepository;

	@Autowired
	private GameBuildRepository gameBuildRepository;

	@Autowired
	private SurveyRepository surveyRepository;

	@Autowired
	private StreamingResourceRepository streamingResourceRepository;

	@Autowired
	private SurveySessionRepository surveySessionRepository;

	// === Mock External Services ===
	@MockitoBean
	private GameLiftService gameLiftService;

	// === Test Fixtures ===
	private Workspace testWorkspace;
	private Game testGame;
	private GameBuild testBuild;
	private Survey testSurvey;
	private StreamingResource testResource;

	@BeforeEach
	void setUp() {
		// 1. Workspace 생성 및 저장
		testWorkspace = Workspace.create("Test Workspace", "테스트 워크스페이스");
		workspaceRepository.save(testWorkspace);

		// 2. Game 생성 및 저장
		testGame = Game.builder()
			.workspace(testWorkspace)
			.name("Test Game")
			.context("테스트용 게임")
			.build();
		gameRepository.save(testGame);

		// 3. GameBuild 생성 및 저장 (UPLOADED 상태, Windows OS)
		testBuild = GameBuild.builder()
			.game(testGame)
			.uuid(UUID.randomUUID())
			.version("1.0.0")
			.build();
		testBuild.markAsUploaded(10, 1024L, "WINDOWS", "/game/test.exe");
		gameBuildRepository.save(testBuild);

		// 4. Survey 생성 및 저장 (ACTIVE 상태)
		testSurvey = Survey.builder()
			.game(testGame)
			.name("Test Survey")
			.build();
		surveyRepository.save(testSurvey);
		testSurvey.activate();
		surveyRepository.save(testSurvey);

		// 5. StreamingResource 생성 및 저장 (READY 상태)
		testResource = StreamingResource.builder()
			.survey(testSurvey)
			.build(testBuild)
			.instanceType("gen4n_win2022")
			.maxCapacity(10)
			.build();
		testResource.assignApplication("arn:aws:gamelift:streams:us-east-1:123456789012:application/app-test-123");
		testResource.assignStreamGroup("arn:aws:gamelift:streams:us-east-1:123456789012:streamgroup/sg-test-123");
		streamingResourceRepository.save(testResource);
	}

	// === Helper Methods ===

	/**
	 * API 응답 JSON에서 survey_session_uuid를 추출합니다.
	 */
	private UUID extractSessionUuidFromResponse(MvcResult result) throws Exception {
		String responseBody = result.getResponse().getContentAsString();
		JsonNode jsonNode = objectMapper.readTree(responseBody);
		String uuidString = jsonNode.path("result").path("survey_session_uuid").asText();
		return UUID.fromString(uuidString);
	}

	/**
	 * 테스트용 CONNECTED 상태 SurveySession을 생성합니다.
	 */
	private SurveySession createConnectedSession(String awsSessionId) {
		SurveySession session = SurveySession.builder()
			.survey(testSurvey)
			.build();
		session.connect(awsSessionId);
		return surveySessionRepository.save(session);
	}

	@Nested
	@DisplayName("GET /surveys/{surveyUuid}/session - 세션 가용성 확인")
	class CheckSessionAvailability {

		@Test
		@DisplayName("ACTIVE 상태의 Survey와 READY 상태의 Resource가 있으면 available 응답")
		void shouldReturnAvailable_WhenSurveyIsActiveAndResourceIsReady() throws Exception {
			// when & then
			mockMvc.perform(get("/surveys/{surveyUuid}/session", testSurvey.getUuid()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.survey_uuid").value(testSurvey.getUuid().toString()))
				.andExpect(jsonPath("$.result.game_name").value(testGame.getName()))
				.andExpect(jsonPath("$.result.is_available").value(true))
				.andExpect(jsonPath("$.result.stream_settings").exists());
		}

		@Test
		@DisplayName("Survey가 ACTIVE 상태가 아니면 unavailable 응답")
		void shouldReturnUnavailable_WhenSurveyIsNotActive() throws Exception {
			// given - Survey를 CLOSED 상태로 변경
			testSurvey.close();
			surveyRepository.save(testSurvey);

			// when & then
			mockMvc.perform(get("/surveys/{surveyUuid}/session", testSurvey.getUuid()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.is_available").value(false))
				.andExpect(jsonPath("$.result.stream_settings").doesNotExist());
		}

		@Test
		@DisplayName("StreamingResource가 없으면 unavailable 응답")
		void shouldReturnUnavailable_WhenResourceDoesNotExist() throws Exception {
			// given - Resource 삭제
			streamingResourceRepository.delete(testResource);

			// when & then
			mockMvc.perform(get("/surveys/{surveyUuid}/session", testSurvey.getUuid()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.is_available").value(false));
		}

		@Test
		@DisplayName("존재하지 않는 Survey UUID로 요청하면 404 응답")
		void shouldReturn404_WhenSurveyNotFound() throws Exception {
			// given
			UUID nonExistentUuid = UUID.randomUUID();

			// when & then
			mockMvc.perform(get("/surveys/{surveyUuid}/session", nonExistentUuid))
				.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("POST /surveys/{surveyUuid}/signal - WebRTC 시그널링")
	class ProcessSignal {

		@Test
		@DisplayName("유효한 시그널 요청 시 SurveySession 생성 및 AWS 연동 성공")
		void shouldCreateSessionAndReturnSignalResponse() throws Exception {
			// given
			long initialSessionCount = surveySessionRepository.count();

			SignalRequest request = new SignalRequest(TEST_SIGNAL_REQUEST);
			String requestBody = objectMapper.writeValueAsString(request);

			// Mock AWS 응답 - Fixture의 실제 값 사용
			given(gameLiftService.startStreamSession(
				eq(testResource.getAwsStreamGroupId()),
				eq(testResource.getAwsApplicationId()),
				eq(TEST_SIGNAL_REQUEST)))
				.willReturn(StartStreamSessionResponse.builder()
					.arn(MOCK_AWS_SESSION_ARN)
					.signalResponse(MOCK_SIGNAL_RESPONSE)
					.build());

			// when - API 호출 및 응답 캡처
			MvcResult result = mockMvc.perform(post("/surveys/{surveyUuid}/signal", testSurvey.getUuid())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.signal_response").value(MOCK_SIGNAL_RESPONSE))
				.andExpect(jsonPath("$.result.survey_session_uuid").exists())
				.andExpect(jsonPath("$.result.expires_in_seconds").value(120))
				.andReturn();

			// then - DB에 SurveySession이 1개 추가되었는지 검증
			long finalSessionCount = surveySessionRepository.count();
			assertThat(finalSessionCount).isEqualTo(initialSessionCount + 1);

			// API 응답에서 UUID 추출 후 정확한 세션 조회
			UUID createdSessionUuid = extractSessionUuidFromResponse(result);
			Optional<SurveySession> savedSession = surveySessionRepository.findByUuid(createdSessionUuid);

			assertThat(savedSession).isPresent();
			assertThat(savedSession.get().getStatus()).isEqualTo(SessionStatus.CONNECTED);
			assertThat(savedSession.get().getAwsSessionId()).isEqualTo(MOCK_AWS_SESSION_ARN);
			assertThat(savedSession.get().getSurvey().getId()).isEqualTo(testSurvey.getId());

			// GameLiftService 호출 검증 - Fixture Getter 사용
			verify(gameLiftService, times(1)).startStreamSession(
				testResource.getAwsStreamGroupId(),
				testResource.getAwsApplicationId(),
				TEST_SIGNAL_REQUEST);
		}

		@Test
		@DisplayName("StreamingResource가 없으면 에러 응답")
		void shouldReturnError_WhenResourceNotAvailable() throws Exception {
			// given - Resource 삭제
			streamingResourceRepository.delete(testResource);

			SignalRequest request = new SignalRequest(TEST_SIGNAL_REQUEST);
			String requestBody = objectMapper.writeValueAsString(request);

			// when & then - 리소스가 없으면 에러 반환
			mockMvc.perform(post("/surveys/{surveyUuid}/signal", testSurvey.getUuid())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().is4xxClientError());
		}

		@Test
		@DisplayName("signal_request가 비어있으면 400 응답")
		void shouldReturn400_WhenSignalRequestIsBlank() throws Exception {
			// given
			SignalRequest request = new SignalRequest("");
			String requestBody = objectMapper.writeValueAsString(request);

			// when & then
			mockMvc.perform(post("/surveys/{surveyUuid}/signal", testSurvey.getUuid())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("GET /surveys/{surveyUuid}/session/status - 세션 상태 확인 (Heartbeat)")
	class GetSessionStatus {

		@Test
		@DisplayName("CONNECTED 상태의 세션이면 active: true 응답")
		void shouldReturnActive_WhenSessionIsConnected() throws Exception {
			// given - CONNECTED 상태의 세션 생성
			SurveySession session = createConnectedSession(MOCK_AWS_SESSION_ARN);

			// when & then
			mockMvc.perform(get("/surveys/{surveyUuid}/session/status", testSurvey.getUuid())
				.param("survey_session_uuid", session.getUuid().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.is_active").value(true))
				.andExpect(jsonPath("$.result.survey_session_uuid").value(session.getUuid().toString()));
		}

		@Test
		@DisplayName("TERMINATED 상태의 세션이면 active: false 응답")
		void shouldReturnInactive_WhenSessionIsTerminated() throws Exception {
			// given - TERMINATED 상태의 세션 생성
			SurveySession session = SurveySession.builder()
				.survey(testSurvey)
				.build();
			session.connect(MOCK_AWS_SESSION_ARN);
			session.terminate();
			surveySessionRepository.save(session);

			// when & then
			mockMvc.perform(get("/surveys/{surveyUuid}/session/status", testSurvey.getUuid())
				.param("survey_session_uuid", session.getUuid().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.is_active").value(false))
				.andExpect(jsonPath("$.result.survey_session_uuid").isEmpty());
		}

		@Test
		@DisplayName("존재하지 않는 세션 UUID로 요청하면 inactive 응답")
		void shouldReturnInactive_WhenSessionNotFound() throws Exception {
			// given
			UUID nonExistentSessionUuid = UUID.randomUUID();

			// when & then
			mockMvc.perform(get("/surveys/{surveyUuid}/session/status", testSurvey.getUuid())
				.param("survey_session_uuid", nonExistentSessionUuid.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.is_active").value(false));
		}
	}

	@Nested
	@DisplayName("POST /surveys/{surveyUuid}/session/terminate - 세션 종료")
	class TerminateSession {

		@Test
		@DisplayName("유효한 세션 종료 요청 시 세션 종료 및 AWS 연동 성공")
		void shouldTerminateSessionAndCallAws() throws Exception {
			// given - CONNECTED 상태의 세션 생성
			SurveySession session = createConnectedSession(MOCK_AWS_SESSION_ARN);

			TerminateSessionRequest request = new TerminateSessionRequest(
				session.getUuid(),
				"user_exit");
			String requestBody = objectMapper.writeValueAsString(request);

			// when & then
			mockMvc.perform(post("/surveys/{surveyUuid}/session/terminate", testSurvey.getUuid())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.success").value(true));

			// DB에서 세션 상태가 TERMINATED로 변경되었는지 검증
			Optional<SurveySession> terminatedSession = surveySessionRepository.findByUuid(session.getUuid());
			assertThat(terminatedSession).isPresent();
			assertThat(terminatedSession.get().getStatus()).isEqualTo(SessionStatus.TERMINATED);
			assertThat(terminatedSession.get().getTerminatedAt()).isNotNull();

			// GameLiftService 종료 메서드 호출 검증 - Fixture Getter 및 Session Getter 사용
			verify(gameLiftService, times(1)).terminateStreamSession(
				testResource.getAwsStreamGroupId(),
				session.getAwsSessionId());
		}

		@Test
		@DisplayName("이미 종료된 세션에 대한 종료 요청은 무시됨")
		void shouldIgnore_WhenSessionIsAlreadyTerminated() throws Exception {
			// given - 이미 종료된 세션
			SurveySession session = SurveySession.builder()
				.survey(testSurvey)
				.build();
			session.connect(MOCK_AWS_SESSION_ARN);
			session.terminate();
			surveySessionRepository.save(session);

			TerminateSessionRequest request = new TerminateSessionRequest(
				session.getUuid(),
				"user_exit");
			String requestBody = objectMapper.writeValueAsString(request);

			// when & then - 성공 응답 반환 (멱등성)
			mockMvc.perform(post("/surveys/{surveyUuid}/session/terminate", testSurvey.getUuid())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.success").value(true));

			// AWS 종료 메서드는 호출되지 않아야 함
			verify(gameLiftService, times(0)).terminateStreamSession(anyString(), anyString());
		}

		@Test
		@DisplayName("존재하지 않는 세션 UUID로 요청하면 에러 응답")
		void shouldReturnError_WhenSessionNotFound() throws Exception {
			// given
			UUID nonExistentSessionUuid = UUID.randomUUID();

			TerminateSessionRequest request = new TerminateSessionRequest(
				nonExistentSessionUuid,
				"user_exit");
			String requestBody = objectMapper.writeValueAsString(request);

			// when & then
			mockMvc.perform(post("/surveys/{surveyUuid}/session/terminate", testSurvey.getUuid())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("세션 UUID가 없으면 400 응답")
		void shouldReturn400_WhenSessionUuidIsNull() throws Exception {
			// given
			String requestBody = "{\"reason\": \"user_exit\"}";

			// when & then
			mockMvc.perform(post("/surveys/{surveyUuid}/session/terminate", testSurvey.getUuid())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isBadRequest());
		}
	}
}
