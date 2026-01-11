package com.playprobie.api.domain.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.interview.domain.TesterProfile;
import com.playprobie.api.domain.interview.dto.InterviewCreateResponse;
import com.playprobie.api.domain.interview.dto.TesterProfileRequest;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

	@InjectMocks
	private InterviewService interviewService;

	@Mock
	private SurveyRepository surveyRepository;

	@Mock
	private SurveySessionRepository surveySessionRepository;

	@Mock
	private Survey survey;

	@DisplayName("sessionUuid가 없으면 새 세션을 생성한다")
	@Test
	void createNewSession_WhenNoSessionUuid() {
		// given
		UUID surveyUuid = UUID.randomUUID();
		TesterProfileRequest request = TesterProfileRequest.builder()
			.ageGroup("20s")
			.build();

		given(surveyRepository.findByUuid(surveyUuid)).willReturn(Optional.of(survey));
		given(surveySessionRepository.save(any(SurveySession.class))).willAnswer(invocation -> {
			SurveySession s = invocation.getArgument(0);
			// Mocking UUID generation or assuming it's done inside entity
			return s;
		});

		// when
		InterviewCreateResponse response = interviewService.createSession(surveyUuid, request);

		// then
		verify(surveySessionRepository).save(any(SurveySession.class));
		assertThat(response).isNotNull();
	}

	@DisplayName("유효한 sessionUuid가 있으면 기존 세션을 업데이트한다")
	@Test
	void updateExistingSession_WhenSessionUuidExists() {
		// given
		UUID surveyUuid = UUID.randomUUID();
		long surveyId = 1L;

		SurveySession existingSession = SurveySession.builder()
			.survey(survey)
			.testerProfile(TesterProfile.createAnonymous("20s", null, null))
			.build();
		UUID sessionUuid = existingSession.getUuid();

		TesterProfileRequest request = TesterProfileRequest.builder()
			.ageGroup("30s")
			.sessionUuid(sessionUuid)
			.build();

		given(survey.getId()).willReturn(surveyId);
		given(surveyRepository.findByUuid(surveyUuid)).willReturn(Optional.of(survey));
		given(surveySessionRepository.findByUuid(sessionUuid)).willReturn(Optional.of(existingSession));
		given(surveySessionRepository.save(any(SurveySession.class))).willReturn(existingSession);

		// when
		InterviewCreateResponse response = interviewService.createSession(surveyUuid, request);

		// then
		verify(surveySessionRepository).findByUuid(sessionUuid);
		verify(surveySessionRepository).save(existingSession);

		assertThat(response).isNotNull();
		assertThat(response.getSession().getSessionUuid()).isEqualTo(sessionUuid);
	}
}
