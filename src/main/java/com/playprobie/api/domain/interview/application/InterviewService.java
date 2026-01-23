package com.playprobie.api.domain.interview.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.playprobie.api.domain.interview.dao.InterviewLogRepository;
import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.AnswerQuality;
import com.playprobie.api.domain.interview.domain.AnswerValidity;
import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.domain.QuestionType;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.interview.dto.InterviewCreateResponse;
import com.playprobie.api.domain.interview.dto.InterviewHistoryResponse;
import com.playprobie.api.domain.interview.dto.TesterProfileRequest;
import com.playprobie.api.domain.interview.dto.UserAnswerRequest;
import com.playprobie.api.domain.interview.dto.UserAnswerResponse;
import com.playprobie.api.domain.interview.dto.common.SessionInfo;
import com.playprobie.api.domain.survey.dao.FixedQuestionRepository;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.global.error.exception.EntityNotFoundException;
import com.playprobie.api.global.error.exception.SessionClosedException;
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
	private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

	@Transactional
	public InterviewCreateResponse createSession(UUID surveyUuid,
		TesterProfileRequest profileRequest) {
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

		// 새 세션 생성 (Default)
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
		log.info("Initialized session state: sessionId={}, fixedQuestionId={}, order={}", sessionUuid,
			firstQuestion.fixedQId(), firstQuestion.qOrder());

		return firstQuestion;
	}

	public FixedQuestionResponse getQuestionById(Long fixedQuestionId) {
		return fixedQuestionRepository.findById(fixedQuestionId)
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
	public void saveTailQuestionLog(String sessionId, Long fixedQuestionId, String tailQuestionText,
		int tailQuestionCount) {
		SurveySession surveySession = surveySessionRepository.findByUuid(UUID.fromString(sessionId))
			.orElseThrow(SessionNotFoundException::new);

		// 해당 고정 질문 내에서의 최대 turnNum을 조회하여 +1
		Integer maxTurnNum = interviewLogRepository.findMaxTurnNumBySessionIdAndFixedQuestionId(
			surveySession.getId(), fixedQuestionId);
		int nextTurnNum = (maxTurnNum != null ? maxTurnNum : 0) + 1;

		InterviewLog interviewLog = InterviewLog.builder()
			.session(surveySession)
			.fixedQuestionId(fixedQuestionId)
			.turnNum(nextTurnNum)
			.type(QuestionType.TAIL)
			.questionText(tailQuestionText)
			.answerText(null) // 아직 응답 없음
			.build();

		interviewLogRepository.save(interviewLog);
		log.info("Saved tail question log for session: {}, fixedQuestionId: {}, turnNum: {}", sessionId,
			fixedQuestionId,
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

		// 0. [Blocking] Check if session is already finished
		if (session.getStatus().isFinished()) {
			log.warn("Attempt to update finished session: {}", sessionId);
			throw new SessionClosedException();
		}

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
				log.info("[LEGACY_INIT] Set state to fixedQuestionId={}, turnNum={}", expectedFixedQId,
					expectedTurnNum);
			}
		}

		Long actualFixedQId = request.getFixedQId();
		Integer actualTurnNum = request.getTurnNum();

		// FixedQId 불일치 수정
		if (expectedFixedQId != null && !expectedFixedQId.equals(actualFixedQId)) {
			log.warn(
				"[STATE_MISMATCH] sessionId={}, fixedQuestionId: client={}, server={}. Correcting to SERVER value.",
				sessionId, actualFixedQId, expectedFixedQId);
			actualFixedQId = expectedFixedQId;
		}

		// TurnNum 불일치 수정
		if (expectedTurnNum != null && !expectedTurnNum.equals(actualTurnNum)) {
			log.warn("[STATE_MISMATCH] sessionId={}, turnNum: client={}, server={}. Correcting to SERVER value.",
				sessionId, actualTurnNum, expectedTurnNum);
			actualTurnNum = expectedTurnNum;
		}

		log.info("[ANSWER_SAVE] sessionId={}, fixedQuestionId={}, turnNum={}, answer={}", sessionId, actualFixedQId,
			actualTurnNum, request.getAnswerText());

		// 2.[멱등성] upsert 로직
		Optional<InterviewLog> existingLog = interviewLogRepository.findBySessionIdAndFixedQuestionIdAndTurnNum(
			session.getId(), actualFixedQId, actualTurnNum);

		InterviewLog savedLog;
		if (existingLog.isPresent()) {
			// 로그 업데이트
			InterviewLog tailLog = existingLog.get();
			tailLog.updateAnswer(request.getAnswerText());
			savedLog = interviewLogRepository.save(tailLog);
			log.info("[LOG_UPDATE] Updated existing log: id={}, turnNum={}", savedLog.getId(), savedLog.getTurnNum());
		} else {
			// 새 로그 생성
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

		// 3.[상태 업데이트] 유효한 답변 처리 후 항상 턴 번호를 증가시킵니다.
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

	/**
	 * 재입력 요청(RETRY) 질문을 InterviewLog에 저장합니다.
	 * AI 서버에서 retry_request 이벤트를 받았을 때 호출됩니다.
	 */
	@Transactional
	public void saveRetryQuestionLog(String sessionId, Long fixedQuestionId, String questionText) {
		SurveySession surveySession = surveySessionRepository.findByUuid(UUID.fromString(sessionId))
			.orElseThrow(SessionNotFoundException::new);

		Integer maxTurnNum = interviewLogRepository.findMaxTurnNumBySessionIdAndFixedQuestionId(
			surveySession.getId(), fixedQuestionId);
		int nextTurnNum = (maxTurnNum != null ? maxTurnNum : 0) + 1;

		InterviewLog interviewLog = InterviewLog.builder()
			.session(surveySession)
			.fixedQuestionId(fixedQuestionId)
			.turnNum(nextTurnNum)
			.type(QuestionType.RETRY)
			.questionText(questionText)
			.answerText(null)
			.build();

		interviewLogRepository.save(interviewLog);
		log.info("Saved RETRY question log for session: {}, fixedQuestionId: {}, turnNum: {}", sessionId,
			fixedQuestionId,
			nextTurnNum);
	}

	/**
	 * 특정 세션 + 고정질문 내에서의 RETRY 질문 횟수를 조회합니다.
	 */
	public int getRetryCount(String sessionId, Long fixedQuestionId) {
		SurveySession session = surveySessionRepository.findByUuid(UUID.fromString(sessionId))
			.orElseThrow(SessionNotFoundException::new);

		List<InterviewLog> logs = interviewLogRepository.findBySessionIdAndFixedQuestionIdOrderByTurnNumAsc(
			session.getId(), fixedQuestionId);

		return (int)logs.stream()
			.filter(log -> log.getType() == QuestionType.RETRY)
			.count();
	}

	/**
	 * 현재 고정질문에 대한 대화 내역(History)을 조회합니다.
	 * 포맷: [{"question": "Q", "answer": "A"}, ...]
	 */
	public List<Map<String, String>> getConversationHistory(String sessionId, Long fixedQuestionId) {
		SurveySession session = surveySessionRepository.findByUuid(UUID.fromString(sessionId))
			.orElseThrow(SessionNotFoundException::new);

		List<InterviewLog> logs = interviewLogRepository.findBySessionIdAndFixedQuestionIdOrderByTurnNumAsc(
			session.getId(), fixedQuestionId);

		List<Map<String, String>> history = new java.util.ArrayList<>();

		for (InterviewLog log : logs) {
			// 질문과 답변이 모두 있는 경우에만 히스토리에 추가 (또는 기획에 따라 답변이 없어도 질문만 추가 가능)
			// 현재 Python 로직은 Q/A 쌍을 기대하므로 단순화하여 매핑
			if (log.getQuestionText() != null) {
				Map<String, String> qaPair = new java.util.HashMap<>();
				qaPair.put("question", log.getQuestionText());
				qaPair.put("answer", log.getAnswerText() != null ? log.getAnswerText() : "");
				history.add(qaPair);
			}
		}

		return history;
	}

	/**
	 * 특정 세션 + 고정질문 + 턴번호의 로그에 유효성/품질 평가 결과를 업데이트합니다.
	 * AI 서버에서 validity_result, quality_result 이벤트를 받은 후 호출됩니다.
	 *
	 * @param answerTurnNum 답변이 저장된 로그의 턴 번호 (방금 답변한 턴)
	 */
	@Transactional
	public void updateLogValidityQuality(String sessionId, Long fixedQuestionId, int answerTurnNum,
		AnswerValidity validity,
		AnswerQuality quality) {

		SurveySession session = surveySessionRepository.findByUuid(UUID.fromString(sessionId))
			.orElseThrow(SessionNotFoundException::new);

		// 특정 턴 번호의 로그 조회 (답변이 저장된 로그)
		java.util.Optional<InterviewLog> logOpt = interviewLogRepository.findBySessionIdAndFixedQuestionIdAndTurnNum(
			session.getId(), fixedQuestionId, answerTurnNum);

		if (logOpt.isEmpty()) {
			log.warn(
				"[VALIDITY_QUALITY] Log not found for sessionId={}, fixedQuestionId={}, turnNum={}. Skipping update.",
				sessionId, fixedQuestionId, answerTurnNum);
			return;
		}

		InterviewLog targetLog = logOpt.get();
		targetLog.updateValidityAndQuality(validity, quality);
		interviewLogRepository.save(targetLog);

		log.info(
			"[VALIDITY_QUALITY] Updated log: sessionId={}, fixedQuestionId={}, turnNum={}, logId={}, validity={}, quality={}",
			sessionId, fixedQuestionId, answerTurnNum, targetLog.getId(), validity, quality);
	}

	/**
	 * AI 세션 시작을 위한 컨텍스트(GameInfo, TesterProfile)를 조회합니다.
	 */
	@Transactional
	public com.playprobie.api.domain.interview.dto.SessionAiContext getSessionAiContext(String sessionId) {
		SurveySession session = surveySessionRepository.findByUuid(UUID.fromString(sessionId))
			.orElseThrow(SessionNotFoundException::new);

		Survey survey = session.getSurvey();
		com.playprobie.api.domain.game.domain.Game game = survey.getGame();

		// 1. Game Info 구성
		Map<String, Object> gameInfo = new java.util.HashMap<>();
		gameInfo.put("game_name", game.getName());
		gameInfo.put("game_genre", game.getGenres().stream().map(Enum::name).toList());
		gameInfo.put("game_context", game.getContext());
		gameInfo.put("target_theme", survey.getThemePriorities()); // Survey에서 가져옴

		// extracted_elements 파싱
		try {
			if (game.getExtractedElements() != null && !game.getExtractedElements().isBlank()) {
				Map<String, Object> elements = objectMapper.readValue(game.getExtractedElements(),
					new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
				gameInfo.put("extracted_elements", elements);
			}
		} catch (Exception e) {
			log.warn("Failed to parse extracted_elements for game {}: {}", game.getId(), e.getMessage());
		}

		// 2. Tester Profile 구성
		com.playprobie.api.infra.ai.dto.request.AiSessionStartRequest.TesterProfileDto profileDto = null;
		if (session.getTesterProfile() != null) {
			com.playprobie.api.domain.interview.domain.TesterProfile p = session.getTesterProfile();
			profileDto = com.playprobie.api.infra.ai.dto.request.AiSessionStartRequest.TesterProfileDto.builder()
				.testerId(p.getTesterId())
				.ageGroup(p.getAgeGroup())
				.gender(p.getGender())
				.preferGenre(p.getPreferGenre())
				.build();
		}

		return com.playprobie.api.domain.interview.dto.SessionAiContext.builder()
			.gameInfo(gameInfo)
			.testerProfile(profileDto)
			.build();
	}
}
