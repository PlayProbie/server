package com.playprobie.api.domain.replay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.playprobie.api.domain.replay.application.ReplayService;
import com.playprobie.api.domain.replay.dto.InputLogDto;
import com.playprobie.api.domain.replay.dto.PresignedUrlRequest;
import com.playprobie.api.domain.replay.dto.PresignedUrlResponse;
import com.playprobie.api.domain.replay.dto.ReplayLogRequest;
import com.playprobie.api.domain.replay.dto.UploadCompleteRequest;

/**
 * ReplayController 단위 테스트
 * Spring Context 없이 MockMvc Standalone 사용
 */
@ExtendWith(MockitoExtension.class)
class ReplayControllerTest {

	private MockMvc mockMvc;

	@Mock
	private ReplayService replayService;

	@InjectMocks
	private ReplayController replayController;

	private ObjectMapper objectMapper;
	private final UUID sessionId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(replayController).build();
		objectMapper = new ObjectMapper();
		objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
	}

	@Nested
	@DisplayName("POST /sessions/{sessionId}/replay/logs")
	class ReceiveInputLogs {

		@Test
		@DisplayName("유효한 로그 배치 수신 시 202 반환")
		void returns202_whenValidLogs() throws Exception {
			// given
			ReplayLogRequest request = new ReplayLogRequest(
				sessionId.toString(),
				"seg_123",
				"https://s3.example.com/video.webm",
				List.of(
					new InputLogDto("KEY_DOWN", 1000L, System.currentTimeMillis(),
						"Space", " ", null, null, null, null, null, null)));

			doNothing().when(replayService).processInputLogs(anyString(), any());

			// when & then
			mockMvc.perform(post("/sessions/{sessionId}/replay/logs", sessionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isAccepted());

			verify(replayService).processInputLogs(eq(sessionId.toString()), any());
		}

		@Test
		@DisplayName("빈 로그 배치도 202 반환")
		void returns202_whenEmptyLogs() throws Exception {
			// given
			ReplayLogRequest request = new ReplayLogRequest(
				sessionId.toString(),
				"seg_123",
				"https://s3.example.com/video.webm",
				List.of());

			// when & then
			mockMvc.perform(post("/sessions/{sessionId}/replay/logs", sessionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isAccepted());
		}
	}

	@Nested
	@DisplayName("POST /sessions/{sessionId}/replay/presigned-url")
	class GeneratePresignedUrl {

		@Test
		@DisplayName("유효한 요청 시 201과 segment_id 반환")
		void returns201_withSegmentId() throws Exception {
			// given
			PresignedUrlRequest request = new PresignedUrlRequest(
				0, 0L, 30000L, "video/webm");

			PresignedUrlResponse response = new PresignedUrlResponse(
				"seg_abc123",
				"https://s3.example.com/presigned-url",
				300);

			when(replayService.generatePresignedUrl(anyString(), any()))
				.thenReturn(response);

			// when & then
			mockMvc.perform(post("/sessions/{sessionId}/replay/presigned-url", sessionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.segment_id").value("seg_abc123"))
				.andExpect(jsonPath("$.s3_url").value("https://s3.example.com/presigned-url"))
				.andExpect(jsonPath("$.expires_in").value(300));
		}
	}

	@Nested
	@DisplayName("POST /sessions/{sessionId}/replay/upload-complete")
	class CompleteUpload {

		@Test
		@DisplayName("유효한 요청 시 200 반환")
		void returns200_onSuccess() throws Exception {
			// given
			UploadCompleteRequest request = new UploadCompleteRequest("seg_abc123");

			doNothing().when(replayService).completeUpload(anyString(), any());

			// when & then
			mockMvc.perform(post("/sessions/{sessionId}/replay/upload-complete", sessionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk());

			verify(replayService).completeUpload(eq(sessionId.toString()), any());
		}
	}
}
