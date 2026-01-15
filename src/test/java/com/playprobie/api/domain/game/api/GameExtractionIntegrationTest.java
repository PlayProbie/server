package com.playprobie.api.domain.game.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.dto.CreateGameRequest;
import com.playprobie.api.domain.game.dto.GameElementExtractRequest;
import com.playprobie.api.domain.game.dto.GameElementExtractResponse;
import com.playprobie.api.domain.user.dao.UserRepository;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.dao.WorkspaceMemberRepository;
import com.playprobie.api.domain.workspace.dao.WorkspaceRepository;
import com.playprobie.api.domain.workspace.domain.Workspace;
import com.playprobie.api.domain.workspace.domain.WorkspaceMember;
import com.playprobie.api.domain.workspace.domain.WorkspaceRole;
import com.playprobie.api.global.security.CustomUserDetails;
import com.playprobie.api.infra.ai.AiClient;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
public class GameExtractionIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private WorkspaceRepository workspaceRepository;

	@Autowired
	private WorkspaceMemberRepository workspaceMemberRepository;

	@Autowired
	private GameRepository gameRepository;

	@MockitoBean
	private AiClient aiClient;

	private User testUser;
	private Workspace testWorkspace;

	@BeforeEach
	void setUp() {
		// User & Workspace setup
		testUser = userRepository.save(User.builder()
			.email("test@example.com")
			.password("password")
			.name("Test User")
			.build());

		testWorkspace = workspaceRepository.save(Workspace.create("Test Workspace", "Desc"));

		workspaceMemberRepository.save(WorkspaceMember.builder()
			.workspace(testWorkspace)
			.user(testUser)
			.role(WorkspaceRole.OWNER)
			.build());

		// Security Context
		CustomUserDetails userDetails = new CustomUserDetails(testUser);
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
	}

	@Test
	@DisplayName("POST /games/extract-elements - 성공적으로 요소를 추출해야 함")
	void extractElements_Success() throws Exception {
		// given
		GameElementExtractRequest request = new GameElementExtractRequest(
			"Test Game",
			List.of("Action"),
			"A game where you fight.");

		GameElementExtractResponse mockResponse = new GameElementExtractResponse(
			Map.of("core_mechanic", "Fighting"),
			List.of("core_mechanic"),
			List.of("art_style"),
			List.of());

		given(aiClient.extractGameElements(any(GameElementExtractRequest.class)))
			.willReturn(mockResponse);

		// when & then
		mockMvc.perform(post("/games/extract-elements")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)))
			.andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.elements.core_mechanic").value("Fighting"))
			.andExpect(jsonPath("$.required_fields[0]").value("core_mechanic"))
			.andExpect(jsonPath("$.optional_fields[0]").value("art_style"));
	}

	@Test
	@DisplayName("POST /workspaces/{uuid}/games - 추출된 요소를 포함하여 게임 저장")
	void createGameWithExtractedElements_Success() throws Exception {
		// given
		String extractedJson = "{\"core_mechanic\": \"Fighting\"}";
		CreateGameRequest request = new CreateGameRequest(
			"New Game",
			List.of("ACTION"),
			"Context",
			extractedJson);

		// when
		mockMvc.perform(post("/workspaces/{workspaceUuid}/games", testWorkspace.getUuid())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());

		// then
		Optional<Game> savedGame = gameRepository.findByWorkspaceUuid(testWorkspace.getUuid()).stream()
			.filter(g -> g.getName().equals("New Game"))
			.findFirst();

		assertThat(savedGame).isPresent();
		assertThat(savedGame.get().getExtractedElements()).isEqualTo(extractedJson);
	}
}
