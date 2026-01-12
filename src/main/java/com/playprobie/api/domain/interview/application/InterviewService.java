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
	public InterviewCreateResponse createSession(UUID surveyUuid,
		com.playprobie.api.domain.interview.dto.TesterProfileRequest profileRequest) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(EntityNotFoundException::new);

		if (profileRequest != null && profileRequest.getSessionUuid() != null) {
			Optional<SurveySession> existingSession = surveySessionRepository
				.findByUuid(profileRequest.getSessionUuid());
			if (existingSession.isPresent()) {
				SurveySession session = existingSession.get();
				if (!session.getSurvey().getId().equals(survey.getId())) {
					log.warn("Session {} does not belong to survey {}", profileRequest.getSessionUuid(), surveyUuid);
				}
				session.updateTesterProfile(profileRequest.toEntity());
				surveySessionRepository.save(session);

				log.info("Updated existing session: {}", session.getUuid());
				return InterviewCreateResponse.builder()
					.session(SessionInfo.from(session))
					.sseUrl(InterviewUrlProvider.getStreamUrl(session.getUuid()))
					.build();
			}
		}

		// Create New Session (Default)
		SurveySession surveySession = SurveySession.builder()
			.survey(survey)
			.testerProfile(profileRequest != null ? profileRequest.toEntity() : null)
			.build();

		SurveySession savedSession = surveySessionRepository.save(surveySession);

		return InterviewCreateResponse.builder()
			.session(SessionInfo.from(savedSession))
			.sseUrl(InterviewUrlProvider.getStreamUrl(savedSession.getUuid()))
			.build();
	}

	@Transactional
	public InterviewHistoryResponse getInterviewHistory(Long surveyId, java.util.UUID sessionUuid) {
		SurveySession session = findAndValidateSession(surveyId, sessionUuid);
		List<InterviewLog> logs = interviewLogRepository.findBySessionUuidOrderByTurnNumAsc(sessionUuid);
		String sseUrl = InterviewUrlProvider.getStreamUrl(session.getUuid());
		return InterviewHistoryResponse.assemble(session, logs, sseUrl);
	}

	@Transactional
	public InterviewHistoryResponse getInterviewHistory(java.util.UUID surveyUuid, java.util.UUID sessionUuid) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(EntityNotFoundException::new);
		return getInterviewHistory(survey.getId(), sessionUuid);
	}

	private SurveySession findAndValidateSession(Long surveyId, java.util.UUID sessionUuid) {
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

		FixedQuestionResponse firstQuestion = fixedQuestionRepository
			.findFirstBySurveyIdOrderByOrderAsc(session.getSurvey().getId())
			.map(FixedQuestionResponse::from)
			.orElseThrow(EntityNotFoundException::new);

		// Initialize Session State
		session.updateInterviewState(firstQuestion.fixedQId(), firstQuestion.qOrder(), 1);
		surveySessionRepository.save(session);
		log.info("Initialized session state: sessionId={}, fixedQId={}, order={}", sessionUuid,
			firstQuestion.fixedQId(), firstQuestion.qOrder());

		return firstQuestion;
	}

	public FixedQuestionResponse getQuestionById(Long fixedQId) {
		return fixedQuestionRepository.findById(fixedQId)
			.map(FixedQuestionResponse::from)
			.orElseThrow(EntityNotFoundException::new);
	}

	/**
	 * 특정 설문의 전체 질문 수를 조회합니다.
	 * Option A: FastAPI에 전달하여 마지막 질문 여부 판단에 사용
	 */
	public int getTotalQuestionCount(Long surveyId) {
		return (int)fixedQuestionRepository.countBySurveyId(surveyId);
	}

	public Optional<FixedQuestionResponse> getNextQuestion(String sessionId, int currentOrder) {
		UUID uuid = UUID.fromString(sessionId);
		SurveySession session = surveySessionRepository.findByUuid(uuid)
			.orElseThrow(SessionNotFoundException::new);

		Optional<FixedQuestionResponse> nextQuestionOpt = fixedQuestionRepository
			.findFirstBySurveyIdAndOrderGreaterThanOrderByOrderAsc(
				session.getSurvey().getId(), currentOrder)
			.map(FixedQuestionResponse::from);

		// 다음 질문 존재하면 session-state 업데이트
		nextQuestionOpt.ifPresent(nextQuestion -> {
			session.moveToNextQuestion(nextQuestion.fixedQId(), nextQuestion.qOrder());
			surveySessionRepository.save(session);
			log.info("Moved to next question: sessionId={}, nextFixedQId={}, nextOrder={}", sessionId,
				nextQuestion.fixedQId(), nextQuestion.qOrder());
		});

		return nextQuestionOpt;
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

	/**
	 * 사용자 답변을 InterviewLog에 저장합니다.
	 * - 고정질문 응답(turnNum=1): 새 레코드 생성
	 * - 꼬리질문 응답(turnNum>1): 기존 레코드 업데이트 또는 새 레코드 생성
	 */
	@Transactional
	public UserAnswerResponse saveInterviewLog(String sessionId, UserAnswerRequest request,
		FixedQuestionResponse currentQuestion) {
		UUID uuid = UUID.fromString(sessionId);
		SurveySession session = surveySessionRepository.findByUuid(uuid)
			.orElseThrow(() -> new RuntimeException("Session not found"));

		// 1. [Server-Side Authority] Validate & Correct State
		Long expectedFixedQId = session.getCurrentFixedQId();
		Integer expectedTurnNum = session.getCurrentTurnNum();

		// [Legacy Support] If session state is null (old data), initialize it lazily
		if (expectedFixedQId == null) {
			log.warn("[LEGACY_SESSION] Initializing state for sessionId={}", sessionId);
			// Assume the first question's ID as current if unknown, or rely on request context (risky but necessary for legacy)
			// Better mitigation: Fetch 1st fixed question if not exists
			if (request.getFixedQId() != null) {
				expectedFixedQId = request.getFixedQId(); // Trust request for legacy fallback
				expectedTurnNum = request.getTurnNum();
				session.updateInterviewState(expectedFixedQId, 1, expectedTurnNum); // Order is unknown, assume 1? Or just don't set order
				log.info("[LEGACY_INIT] Set state to fixedQId={}, turnNum={}", expectedFixedQId, expectedTurnNum);
			}
		}

		Long actualFixedQId = request.getFixedQId();
		Integer actualTurnNum = request.getTurnNum();

		// Validation: Fix FixedQId Mismatch
		if (expectedFixedQId != null && !expectedFixedQId.equals(actualFixedQId)) {
			log.warn("[STATE_MISMATCH] sessionId={}, fixedQId: client={}, server={}. Correcting to SERVER value.",
				sessionId, actualFixedQId, expectedFixedQId);
			actualFixedQId = expectedFixedQId;
		}

		// Validation: Fix TurnNum Mismatch
		if (expectedTurnNum != null && !expectedTurnNum.equals(actualTurnNum)) {
			log.warn("[STATE_MISMATCH] sessionId={}, turnNum: client={}, server={}. Correcting to SERVER value.",
				sessionId, actualTurnNum, expectedTurnNum);
			actualTurnNum = expectedTurnNum;
		}

		log.info("[ANSWER_SAVE] sessionId={}, fixedQId={}, turnNum={}, answer={}", sessionId, actualFixedQId,
			actualTurnNum, request.getAnswerText());

		// 2. [Idempotency] Upsert Logic
		Optional<InterviewLog> existingLog = interviewLogRepository.findBySessionIdAndFixedQuestionIdAndTurnNum(
			session.getId(), actualFixedQId, actualTurnNum);

		InterviewLog savedLog;
		if (existingLog.isPresent()) {
			// Update existing log
			InterviewLog tailLog = existingLog.get();
			tailLog.updateAnswer(request.getAnswerText());
			savedLog = interviewLogRepository.save(tailLog);
			log.info("[LOG_UPDATE] Updated existing log: id={}, turnNum={}", savedLog.getId(), savedLog.getTurnNum());
		} else {
			// Create new log
			QuestionType type = (actualTurnNum == 1) ? QuestionType.FIXED : QuestionType.TAIL;
			String qText = (actualTurnNum == 1) ? currentQuestion.qContent() : request.getQuestionText();

			InterviewLog newLog = InterviewLog.builder()
				.session(session)
				.fixedQuestionId(actualFixedQId)
				.turnNum(actualTurnNum)
				.type(type)
				.questionText(qText)
				.answerText(request.getAnswerText())
				.build();
			savedLog = interviewLogRepository.save(newLog);
			log.info("[LOG_CREATE] Created new log: id={}, turnNum={}", savedLog.getId(), savedLog.getTurnNum());

		}

		// 3. [State Update] Always increment Turn Number after valid answer processing
		session.incrementTurnNum();
		surveySessionRepository.save(session);
		log.info("[STATE_UPDATE] Incremented session turnNum to {}", session.getCurrentTurnNum());

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
