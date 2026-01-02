package com.playprobie.api.domain.interview.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.playprobie.api.domain.interview.dao.InterviewLogRepository;
import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.interview.dto.InterviewHistoryResponse;
import com.playprobie.api.domain.interview.dto.InterviewCreateResponse;
import com.playprobie.api.domain.interview.dto.UserAnswerResponse;
import com.playprobie.api.domain.interview.dto.UserAnswerRequest;
import com.playprobie.api.domain.interview.dto.common.SessionInfo;
import com.playprobie.api.domain.survey.dao.FixedQuestionRepository;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.global.util.InterviewUrlProvider;
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
	private final FixedQuestionRepository fixedQuestionRepository;

	@Transactional
	public InterviewCreateResponse createSession(UUID surveyUuid) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
				.orElseThrow(EntityNotFoundException::new);

		SurveySession surveySession = SurveySession.builder()
				.survey(survey)
				.testerProfile(null)
				.build();

		SurveySession savedSession = surveySessionRepository.save(surveySession);

		return InterviewCreateResponse.builder()
				.session(SessionInfo.from(savedSession))
				.sseUrl(InterviewUrlProvider.getStreamUrl(savedSession.getUuid()))
				.build();
	}

	@Transactional
	public InterviewHistoryResponse getInterviewHistory(Long surveyId, UUID sessionUuid) {
		SurveySession session = findAndValidateSession(surveyId, sessionUuid);
		List<InterviewLog> logs = interviewLogRepository.findBySessionUuidOrderByTurnNumAsc(sessionUuid);
		String sseUrl = InterviewUrlProvider.getStreamUrl(session.getUuid());
		return InterviewHistoryResponse.assemble(session, logs, sseUrl);
	}

	private SurveySession findAndValidateSession(Long surveyId, UUID sessionUuid) {
		SurveySession session = surveySessionRepository.findByUuid(sessionUuid)
				.orElseThrow(SessionNotFoundException::new);
		session.validateSurveyId(surveyId);
		return session;
	}

	@Transactional
	public FixedQuestionResponse getFirstQuestion(String sessionUuid) {
		UUID uuid = UUID.fromString(sessionUuid);

		SurveySession session = surveySessionRepository.findByUuid(uuid)
				.orElseThrow(SessionNotFoundException::new);

		return fixedQuestionRepository.findFirstBySurveyIdOrderByOrderAsc(session.getSurvey().getId())
				.map(FixedQuestionResponse::from)
				.orElseThrow(EntityNotFoundException::new);
	}

	public FixedQuestionResponse getQuestionById(Long fixedQId) {
		return fixedQuestionRepository.findById(fixedQId)
				.map(FixedQuestionResponse::from)
				.orElseThrow(EntityNotFoundException::new);
	}

	public Optional<FixedQuestionResponse> getNextQuestion(String sessionId, int currentOrder) {
		UUID uuid = UUID.fromString(sessionId);
		SurveySession session = surveySessionRepository.findByUuid(uuid)
				.orElseThrow(SessionNotFoundException::new);

		return fixedQuestionRepository
				.findFirstBySurveyIdAndOrderGreaterThanOrderByOrderAsc(
						session.getSurvey().getId(), currentOrder)
				.map(FixedQuestionResponse::from);
	}

	public UserAnswerResponse saveInterviewLog(String sessionId, UserAnswerRequest request,
			FixedQuestionResponse currentQuestion) {
		// ---------------------------------------------------//
		// String seesionId = sessionId; //uuid
		// long fixedQId = currentQuestion.fixedQId();
		// int turnNum = request.getTurnNum(); // null 체크 추가
		// QuestionType qType = null;
		// String questionText = currentQuestion.qContent();
		// String answerText = request.getAnswerText();
		// int tokensUsed = 0;
		// ---------------------------------------------------//
		// TODO: 예외처리
		SurveySession surveySession = surveySessionRepository.findByUuid(UUID.fromString(sessionId))
				.orElseThrow(() -> new RuntimeException("Session not found"));

		InterviewLog interviewLog = InterviewLog.builder()
				.session(surveySession)
				.fixedQuestionId(currentQuestion.fixedQId())
				.turnNum(request.getTurnNum())
				// TODO: AI-Server 작업 완료 후 type지정
				.type(null)
				.questionText(currentQuestion.qContent())
				.answerText(request.getAnswerText())
				.build();

		InterviewLog savedLog = interviewLogRepository.save(interviewLog);

		return UserAnswerResponse.of(
				savedLog.getTurnNum(),
				String.valueOf(savedLog.getType()),
				savedLog.getFixedQuestionId(),
				savedLog.getQuestionText(),
				savedLog.getAnswerText());
	}
}
