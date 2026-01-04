package com.playprobie.api.domain.interview.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.playprobie.api.domain.interview.dao.InterviewLogRepository;
import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.domain.QuestionType;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.interview.dto.InterviewCreateResponse;
import com.playprobie.api.domain.interview.dto.InterviewHistoryResponse;
import com.playprobie.api.domain.interview.dto.UserAnswerRequest;
import com.playprobie.api.domain.interview.dto.UserAnswerResponse;
import com.playprobie.api.domain.interview.dto.common.SessionInfo;
import com.playprobie.api.domain.survey.dao.FixedQuestionRepository;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.global.error.exception.EntityNotFoundException;
import com.playprobie.api.global.error.exception.SessionNotFoundException;
import com.playprobie.api.global.util.InterviewUrlProvider;

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

	/**
	 * 꼬리 질문을 InterviewLog에 저장합니다.
	 * AI 서버에서 generate_tail_complete 이벤트를 받았을 때 호출됩니다.
	 */
	@Transactional
	public void saveTailQuestionLog(String sessionId, Long fixedQId, String tailQuestionText, int tailQuestionCount) {
		SurveySession surveySession = surveySessionRepository.findByUuid(UUID.fromString(sessionId))
				.orElseThrow(SessionNotFoundException::new);

		// 해당 고정 질문 내에서의 최대 turnNum을 조회하여 +1
		Integer maxTurnNum = interviewLogRepository.findMaxTurnNumBySessionIdAndFixedQId(
				surveySession.getId(), fixedQId);
		int nextTurnNum = (maxTurnNum != null ? maxTurnNum : 0) + 1;

		InterviewLog interviewLog = InterviewLog.builder()
				.session(surveySession)
				.fixedQuestionId(fixedQId)
				.turnNum(nextTurnNum)
				.type(QuestionType.TAIL)
				.questionText(tailQuestionText)
				.answerText(null) // 아직 응답 없음
				.build();

		interviewLogRepository.save(interviewLog);
		log.info("Saved tail question log for session: {}, fixedQId: {}, turnNum: {}", sessionId, fixedQId,
				nextTurnNum);
	}

	public UserAnswerResponse saveInterviewLog(String sessionId, UserAnswerRequest request,
			FixedQuestionResponse currentQuestion) {
		SurveySession surveySession = surveySessionRepository.findByUuid(UUID.fromString(sessionId))
				.orElseThrow(() -> new RuntimeException("Session not found"));

		// turn_num > 1이면 꼬리질문 응답 → 기존 레코드 업데이트
		if (request.getTurnNum() > 1) {
			InterviewLog tailLog = interviewLogRepository
					.findBySessionIdAndFixedQuestionIdAndTurnNum(
							surveySession.getId(),
							request.getFixedQId(),
							request.getTurnNum())
					.orElseThrow(() -> new RuntimeException("Tail question log not found"));

			tailLog.updateAnswer(request.getAnswerText());
			InterviewLog savedLog = interviewLogRepository.save(tailLog);

			return UserAnswerResponse.of(
					savedLog.getTurnNum(),
					String.valueOf(savedLog.getType()),
					savedLog.getFixedQuestionId(),
					savedLog.getQuestionText(),
					savedLog.getAnswerText());
		}

		// 고정질문 응답인 경우 새 레코드 생성
		InterviewLog interviewLog = InterviewLog.builder()
				.session(surveySession)
				.fixedQuestionId(currentQuestion.fixedQId())
				.turnNum(request.getTurnNum())
				.type(QuestionType.FIXED)
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

	@Transactional
	public void completeSession(String sessionUuid) {
		UUID uuid = UUID.fromString(sessionUuid);
		SurveySession session = surveySessionRepository.findByUuid(uuid)
				.orElseThrow(SessionNotFoundException::new);

		session.complete();
		log.info("Session completed: {}", sessionUuid);
	}

	// 세션의 모든 로그를 fixedQuestionId별로 그룹핑
	public Map<Long, List<InterviewLog>> getLogsGroupedByFixedQuestion(String sessionUuid) {
		UUID uuid = UUID.fromString(sessionUuid);
		List<InterviewLog> logs = interviewLogRepository
				.findBySessionUuidOrderByFixedQuestionIdAscTurnNumAsc(uuid);

		return logs.stream()
				.collect(Collectors.groupingBy(InterviewLog::getFixedQuestionId));
	}

	// 세션 UUID로 Survey ID 조회
	public Long getSurveyIdBySession(String sessionUuid) {
		UUID uuid = UUID.fromString(sessionUuid);
		SurveySession session = surveySessionRepository.findByUuid(uuid)
				.orElseThrow(SessionNotFoundException::new);

		return session.getSurvey().getId();
	}
}
