package com.playprobie.api.domain.survey.application;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.interview.dao.InterviewLogRepository;
import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.domain.SessionStatus;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.survey.dao.FixedQuestionRepository;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.FixedQuestion;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.dto.SurveyResultDetailResponse;
import com.playprobie.api.domain.survey.dto.SurveyResultListResponse;
import com.playprobie.api.domain.survey.dto.SurveyResultSummaryResponse;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.application.WorkspaceSecurityManager;
import com.playprobie.api.global.error.exception.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyResultService {

	private final SurveyRepository surveyRepository;
	private final SurveySessionRepository sessionRepository;
	private final FixedQuestionRepository fixedQuestionRepository;
	private final InterviewLogRepository interviewLogRepository;
	private final WorkspaceSecurityManager securityManager;

	// 전체 응답 요약 (설문 기준)
	public SurveyResultSummaryResponse getSummary(long surveyId, SessionStatus status) {
		long surveyCount = 1; // 단일 설문이므로 1
		long responseCount = sessionRepository.countBySurveyIdAndStatus(surveyId, status);
		return SurveyResultSummaryResponse.of(surveyCount, responseCount);
	}

	public SurveyResultSummaryResponse getSummary(java.util.UUID surveyUuid, SessionStatus status, User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(EntityNotFoundException::new);
		securityManager.validateReadAccess(survey.getGame().getWorkspace(), user);
		return getSummary(survey.getId(), status);
	}

	// 전체 응답 리스트 (커서 페이징)
	public SurveyResultListResponse getResponseList(long surveyId, Long cursor, int size) {
		List<SurveySession> sessions = sessionRepository.findBySurveyIdWithCursor(
			surveyId, cursor, PageRequest.of(0, size + 1));

		boolean hasNext = sessions.size() > size;
		if (hasNext) {
			sessions = sessions.subList(0, size);
		}

		Long nextCursor = hasNext && !sessions.isEmpty()
			? sessions.get(sessions.size() - 1).getId()
			: null;

		List<SurveyResultListResponse.SessionItem> content = sessions.stream()
			.map(session -> {
				var profile = session.getTesterProfile();
				return SurveyResultListResponse.SessionItem.builder()
					.sessionUuid(session.getUuid())
					.surveyName(session.getSurvey().getName())
					.surveyUuid(session.getSurvey().getUuid())
					.testerId(profile != null ? profile.getTesterId() : null)
					.status(session.getStatus())
					.gender(profile != null ? profile.getGender() : null)
					.ageGroup(profile != null ? profile.getAgeGroup() : null)
					.preferGenre(profile != null ? profile.getPreferGenre() : null)
					.endedAt(session.getEndedAt() != null
						? session.getEndedAt().atZone(ZoneId.of("Asia/Seoul"))
							.toOffsetDateTime()
						: null)
					.build();
			})
			.toList();

		return SurveyResultListResponse.builder()
			.content(content)
			.nextCursor(nextCursor)
			.hasNext(hasNext)
			.build();
	}

	public SurveyResultListResponse getResponseList(java.util.UUID surveyUuid, Long cursor, int size, User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(EntityNotFoundException::new);
		securityManager.validateReadAccess(survey.getGame().getWorkspace(), user);
		return getResponseList(survey.getId(), cursor, size);
	}

	// 응답 세부 내용
	public SurveyResultDetailResponse getResponseDetails(long surveyId, long sessionId) {
		SurveySession session = sessionRepository.findById(sessionId)
			.orElseThrow(EntityNotFoundException::new);

		session.validateSurveyId(surveyId);

		SurveyResultDetailResponse.SessionInfo sessionInfo = buildSessionInfo(session);
		List<SurveyResultDetailResponse.FixedQuestionGroup> byFixedQuestion = buildQuestionGroups(sessionId);

		return SurveyResultDetailResponse.builder()
			.session(sessionInfo)
			.byFixedQuestion(byFixedQuestion)
			.build();
	}

	public SurveyResultDetailResponse getResponseDetails(java.util.UUID surveyUuid, java.util.UUID sessionUuid,
		User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
			.orElseThrow(EntityNotFoundException::new);
		securityManager.validateReadAccess(survey.getGame().getWorkspace(), user);

		SurveySession session = sessionRepository.findByUuid(sessionUuid)
			.orElseThrow(EntityNotFoundException::new);
		return getResponseDetails(survey.getId(), session.getId());
	}

	private SurveyResultDetailResponse.SessionInfo buildSessionInfo(SurveySession session) {
		return SurveyResultDetailResponse.SessionInfo.builder()
			.sessionUuid(session.getUuid())
			.surveyName(session.getSurvey().getName())
			.surveyUuid(session.getSurvey().getUuid())
			.testerId(session.getTesterProfile() != null ? session.getTesterProfile().getTesterId()
				: null)
			.status(session.getStatus())
			.endedAt(session.getEndedAt() != null
				? session.getEndedAt().atZone(ZoneId.of("Asia/Seoul"))
					.toOffsetDateTime()
				: null)
			.build();
	}

	private List<SurveyResultDetailResponse.FixedQuestionGroup> buildQuestionGroups(Long sessionId) {
		List<InterviewLog> logs = interviewLogRepository.findBySessionIdOrderByTurnNumAsc(sessionId);

		Map<Long, List<InterviewLog>> groupedLogs = new LinkedHashMap<>();
		for (InterviewLog log : logs) {
			groupedLogs.computeIfAbsent(log.getFixedQuestionId(), k -> new ArrayList<>()).add(log);
		}

		// N+1 최적화: 모든 fixedQuestionId 일괄 조회
		Set<Long> fixedQuestionIds = groupedLogs.keySet();
		Map<Long, String> questionTextMap = fixedQuestionRepository.findAllByIdIn(fixedQuestionIds)
			.stream()
			.collect(Collectors.toMap(FixedQuestion::getId, FixedQuestion::getContent));

		return groupedLogs.entrySet().stream()
			.map(entry -> {
				Long fixedQuestionId = entry.getKey();
				List<InterviewLog> logList = entry.getValue();

				List<SurveyResultDetailResponse.ExcerptItem> excerpt = logList.stream()
					.map(log -> SurveyResultDetailResponse.ExcerptItem.builder()
						.qType(log.getType())
						.questionText(log.getQuestionText())
						.answerText(log.getAnswerText())
						.build())
					.toList();

				return SurveyResultDetailResponse.FixedQuestionGroup.builder()
					.fixedQuestion(questionTextMap.getOrDefault(fixedQuestionId,
						"Unknown Question"))
					.excerpt(excerpt)
					.build();
			})
			.toList();
	}
}
