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

		return fixedQuestionRepository.findFirstBySurveyIdOrderByOrderAsc(session.getSurvey().getId())
			.map(FixedQuestionResponse::from)
			.orElseThrow(EntityNotFoundException::new);
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

	/**
	 * 사용자 답변을 InterviewLog에 저장합니다.
	 * - 고정질문 응답(turnNum=1): 새 레코드 생성
	 * - 꼬리질문 응답(turnNum>1): 기존 레코드 업데이트 또는 새 레코드 생성
	 */
	public UserAnswerResponse saveInterviewLog(String sessionId, UserAnswerRequest request,
		FixedQuestionResponse currentQuestion) {
		// 세션 UUID로 SurveySession 조회 (없으면 예외)
		SurveySession surveySession = surveySessionRepository.findByUuid(UUID.fromString(sessionId))
			.orElseThrow(() -> new RuntimeException("Session not found"));

		// turnNum > 1이면 꼬리질문에 대한 응답임 (turnNum=1은 고정질문)
		if (request.getTurnNum() > 1) {
			// 해당 세션 + 고정질문 + 턴번호로 기존 꼬리질문 레코드 조회 시도
			Optional<InterviewLog> existingLog = interviewLogRepository
				.findBySessionIdAndFixedQuestionIdAndTurnNum(
					surveySession.getId(), // 세션 ID
					request.getFixedQId(), // 고정질문 ID
					request.getTurnNum()); // 턴 번호

			// 기존 레코드가 있으면 → AI가 먼저 꼬리질문을 저장해둔 정상 케이스
			if (existingLog.isPresent()) {
				InterviewLog tailLog = existingLog.get(); // 기존 레코드 가져오기
				tailLog.updateAnswer(request.getAnswerText()); // 답변 텍스트 업데이트
				InterviewLog savedLog = interviewLogRepository.save(tailLog); // DB 저장

				// 응답 DTO 생성하여 반환
				return UserAnswerResponse.of(
					savedLog.getTurnNum(),
					String.valueOf(savedLog.getType()),
					savedLog.getFixedQuestionId(),
					savedLog.getQuestionText(),
					savedLog.getAnswerText());
			}

			// 기존 레코드가 없으면 → Race Condition 발생 (AI 저장보다 사용자 답변이 먼저 도착)
			// 예외를 던지지 않고, 새 레코드를 직접 생성하여 오류 방지
			log.warn("Tail question log not found for turnNum={}. Creating new record. sessionId={}, fixedQId={}",
				request.getTurnNum(), sessionId, request.getFixedQId());

			// 새 꼬리질문 레코드 생성 (답변과 함께)
			InterviewLog newTailLog = InterviewLog.builder()
				.session(surveySession) // 세션 연결
				.fixedQuestionId(request.getFixedQId()) // 고정질문 ID
				.turnNum(request.getTurnNum()) // 턴 번호
				.type(QuestionType.TAIL) // 질문 유형: 꼬리질문
				.questionText(request.getQuestionText()) // 클라이언트가 보낸 질문 텍스트
				.answerText(request.getAnswerText()) // 사용자 답변
				.build();

			InterviewLog savedLog = interviewLogRepository.save(newTailLog); // DB 저장

			return UserAnswerResponse.of(
				savedLog.getTurnNum(),
				String.valueOf(savedLog.getType()),
				savedLog.getFixedQuestionId(),
				savedLog.getQuestionText(),
				savedLog.getAnswerText());
		}

		// turnNum == 1이면 고정질문에 대한 첫 응답 → 항상 새 레코드 생성
		InterviewLog interviewLog = InterviewLog.builder()
			.session(surveySession) // 세션 연결
			.fixedQuestionId(currentQuestion.fixedQId()) // 고정질문 ID
			.turnNum(request.getTurnNum()) // 턴 번호 (항상 1)
			.type(QuestionType.FIXED) // 질문 유형: 고정질문
			.questionText(currentQuestion.qContent()) // 고정질문 내용
			.answerText(request.getAnswerText()) // 사용자 답변
			.build();

		InterviewLog savedLog = interviewLogRepository.save(interviewLog); // DB 저장

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
