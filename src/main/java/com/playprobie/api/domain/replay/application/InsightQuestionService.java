package com.playprobie.api.domain.replay.application;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.replay.dao.AnalysisTagRepository;
import com.playprobie.api.domain.replay.domain.AnalysisTag;
import com.playprobie.api.domain.replay.dto.InsightAnswerResponse;
import com.playprobie.api.domain.replay.dto.InsightCompletePayload;
import com.playprobie.api.domain.replay.dto.InsightQuestionPayload;
import com.playprobie.api.domain.replay.event.InsightPhaseCompleteEvent;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.EntityNotFoundException;
import com.playprobie.api.infra.sse.service.SseEmitterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인사이트 질문 서비스
 * 고정질문 완료 후 인사이트 질문 Phase 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightQuestionService {

	private static final String EVENT_INSIGHT_QUESTION = "insight_question";
	private static final String EVENT_INSIGHT_COMPLETE = "insight_complete";

	private final AnalysisTagRepository analysisTagRepository;
	private final SurveySessionRepository surveySessionRepository;
	private final InsightQuestionGenerator insightQuestionGenerator;
	private final SseEmitterService sseEmitterService;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 세션에 질문하지 않은 인사이트 태그가 있는지 확인
	 */
	@Transactional(readOnly = true)
	public boolean hasUnaskedInsights(String sessionUuid) {
		UUID uuid = UUID.fromString(sessionUuid);
		List<AnalysisTag> unaskedTags = analysisTagRepository.findBySessionUuidAndIsAskedFalse(uuid);
		return !unaskedTags.isEmpty();
	}

	/**
	 * 인사이트 질문 Phase 시작
	 * 가장 마지막 태그 1개만 선택하여 질문 전송
	 *
	 * @return 시작 성공 여부 (인사이트 없으면 false)
	 */
	@Transactional
	public boolean startInsightQuestionPhase(String sessionUuid) {
		UUID uuid = UUID.fromString(sessionUuid);
		SurveySession session = surveySessionRepository.findByUuid(uuid)
			.orElseThrow(() -> new EntityNotFoundException(ErrorCode.SURVEY_SESSION_NOT_FOUND));

		List<AnalysisTag> allTags = analysisTagRepository.findBySessionIdAndIsAskedFalse(session.getId());

		if (allTags.isEmpty()) {
			log.info("[InsightQuestionService] No insights for session: {}", sessionUuid);
			return false;
		}

		// 가장 마지막 태그 1개만 선택
		AnalysisTag lastTag = allTags.get(allTags.size() - 1);
		List<AnalysisTag> selectedTags = List.of(lastTag);

		// 선택된 태그 마킹 및 비선택 태그 스킵 처리
		for (AnalysisTag tag : allTags) {
			if (selectedTags.contains(tag)) {
				tag.markAsSelected();
			} else {
				tag.markAsSkipped();
			}
		}
		analysisTagRepository.saveAll(allTags);

		log.info("[InsightQuestionService] Starting insight phase: session={}, selected={}/{}",
			sessionUuid, selectedTags.size(), allTags.size());

		// 첫번째 질문 전송
		sendInsightQuestion(sessionUuid, selectedTags, 0);

		return true;
	}

	/**
	 * 인사이트 질문에 대한 답변 처리 및 다음 질문/완료 처리
	 *
	 * @param tagId      답변한 태그 ID
	 * @param answerText 사용자 답변
	 * @return 모든 질문 완료 여부
	 */
	@Transactional
	public boolean processInsightAnswer(String sessionUuid, Long tagId, String answerText) {
		AnalysisTag tag = analysisTagRepository.findById(tagId)
			.orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

		// 답변 저장
		tag.markAsAsked(answerText);
		analysisTagRepository.save(tag);

		log.info("[InsightQuestionService] Answer saved: tagId={}, type={}",
			tagId, tag.getInsightType());

		// 남은 인사이트 질문 확인 (선택된 태그 중에서만)
		UUID uuid = UUID.fromString(sessionUuid);
		List<AnalysisTag> remainingTags = analysisTagRepository.findBySessionUuidAndIsSelectedTrueAndIsAskedFalse(uuid);

		if (remainingTags.isEmpty()) {
			// 모든 인사이트 질문 완료
			sendInsightComplete(sessionUuid);
			return true;
		} else {
			// 다음 인사이트 질문 전송
			sendInsightQuestion(sessionUuid, remainingTags, 0);
			return false;
		}
	}

	/**
	 * 인사이트 질문에 대한 답변 처리 및 응답 반환 (REST API용)
	 * 다음 질문은 SSE로 전송되므로 REST 응답에는 완료 여부만 포함
	 *
	 * @param sessionUuid 세션 UUID
	 * @param tagId       답변한 태그 ID
	 * @param answerText  사용자 답변
	 * @return InsightAnswerResponse (완료 여부)
	 */
	@Transactional
	public InsightAnswerResponse processInsightAnswerWithResponse(String sessionUuid, Long tagId, String answerText) {
		AnalysisTag tag = analysisTagRepository.findById(tagId)
			.orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

		// 답변 저장
		tag.markAsAsked(answerText);
		analysisTagRepository.save(tag);

		log.info("[InsightQuestionService] Answer saved: tagId={}, type={}",
			tagId, tag.getInsightType());

		// 남은 인사이트 질문 확인 (선택된 태그 중에서만)
		UUID uuid = UUID.fromString(sessionUuid);
		List<AnalysisTag> remainingTags = analysisTagRepository.findBySessionUuidAndIsSelectedTrueAndIsAskedFalse(uuid);

		if (remainingTags.isEmpty()) {
			// 모든 인사이트 질문 완료 - SSE 이벤트 전송 및 완료 응답 반환
			sendInsightComplete(sessionUuid);
			return InsightAnswerResponse.complete(tagId);
		} else {
			// 다음 인사이트 질문 SSE로 전송
			AnalysisTag nextTag = remainingTags.get(0);
			int turnNum = 2; // 두 번째 질문부터
			int remaining = remainingTags.size() - 1;

			InsightQuestionPayload nextQuestion = insightQuestionGenerator.generate(nextTag, turnNum, remaining);
			sseEmitterService.send(sessionUuid, EVENT_INSIGHT_QUESTION, nextQuestion);

			// REST 응답에는 진행 중 상태만 반환 (다음 질문은 SSE로 전송됨)
			return InsightAnswerResponse.inProgress(tagId);
		}
	}

	/**
	 * 인사이트 질문 SSE 이벤트 전송
	 */
	private void sendInsightQuestion(String sessionUuid, List<AnalysisTag> tags, int index) {
		if (index >= tags.size()) {
			sendInsightComplete(sessionUuid);
			return;
		}

		AnalysisTag tag = tags.get(index);
		int turnNum = index + 1;
		int remaining = tags.size() - turnNum;

		InsightQuestionPayload payload = insightQuestionGenerator.generate(tag, turnNum, remaining);

		sseEmitterService.send(sessionUuid, EVENT_INSIGHT_QUESTION, payload);

		log.info("[InsightQuestionService] Sent insight question: session={}, type={}, turn={}",
			sessionUuid, tag.getInsightType(), turnNum);
	}

	/**
	 * 인사이트 완료 SSE 이벤트 전송
	 */
	private void sendInsightComplete(String sessionUuid) {
		UUID uuid = UUID.fromString(sessionUuid);
		SurveySession session = surveySessionRepository.findByUuid(uuid)
			.orElseThrow(() -> new EntityNotFoundException(ErrorCode.SURVEY_SESSION_NOT_FOUND));

		List<AnalysisTag> allTags = analysisTagRepository.findBySessionId(session.getId());
		long answeredCount = allTags.stream().filter(AnalysisTag::getIsAsked).count();

		InsightCompletePayload payload = new InsightCompletePayload(
			allTags.size(),
			(int)answeredCount);

		sseEmitterService.send(sessionUuid, EVENT_INSIGHT_COMPLETE, payload);

		log.info("[InsightQuestionService] Insight phase complete: session={}, total={}, answered={}",
			sessionUuid, allTags.size(), answeredCount);

		// 클로징 트리거 이벤트 발행
		eventPublisher.publishEvent(new InsightPhaseCompleteEvent(sessionUuid));
	}
}
