package com.playprobie.api.domain.interview.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.playprobie.api.domain.interview.dao.InterviewLogRepository;
import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.interview.domain.TesterProfile;
import com.playprobie.api.domain.interview.dto.InterviewHistoryResponse;
import com.playprobie.api.domain.interview.dto.InterviewStartResponse;
import com.playprobie.api.domain.interview.dto.common.SessionInfo;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.global.error.exception.EntityNotFoundException;
import com.playprobie.api.global.error.exception.SessionNotFoundException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewService {
	private final SurveyRepository surveyRepository;
	private final InterviewLogRepository interviewLogRepository;
	private final SurveySessionRepository surveySessionRepository;

	@Transactional
	public InterviewStartResponse createSession(Long surveyId) {
		Survey survey = surveyRepository.findById(surveyId)
			.orElseThrow(EntityNotFoundException::new);

		/* tester profile 임시 생성 */
		TesterProfile testerProfile = TesterProfile.builder()
			.testerId("f1e2d3c4-b5a6-7980-1234-567890abcdef")
			.ageGroup("20s")
			.gender("M")
			.preferGenre("STRATEGY")
			.build();

		SurveySession session = SurveySession.builder()
			.survey(survey)
			.testerProfile(testerProfile)
			.build();

		SurveySession surveySession = surveySessionRepository.save(session);

		String sseUrl = "/interview/sessions/" + testerProfile.getTesterId() + "/stream";

		return InterviewStartResponse.builder()
			.session(SessionInfo.from(surveySession))
			.sseUrl(sseUrl)
			.build();
	}

	@Transactional
	public InterviewHistoryResponse getInterviewHistory(Long surveyId, Long sessionId) {
		SurveySession session = findAndValidateSession(sessionId, surveyId);
		List<InterviewLog> logs = interviewLogRepository.findBySessionIdOrderByTurnNumAsc(sessionId);
		return InterviewHistoryResponse.assemble(session, logs);
	}

	private SurveySession findAndValidateSession(Long sessionId, Long surveyId) {
		SurveySession session = surveySessionRepository.findById(sessionId)
			.orElseThrow(SessionNotFoundException::new);

		session.validateSurveyId(surveyId);

		return session;
	}
}
