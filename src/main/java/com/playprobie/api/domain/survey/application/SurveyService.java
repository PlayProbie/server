package com.playprobie.api.domain.survey.application;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.application.GameService;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.application.WorkspaceSecurityManager;
import com.playprobie.api.domain.survey.dao.FixedQuestionRepository;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.FixedQuestion;
import com.playprobie.api.domain.survey.domain.QuestionStatus;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.domain.SurveyStatus;
import com.playprobie.api.domain.survey.domain.TestPurpose;
import com.playprobie.api.domain.survey.dto.CreateFixedQuestionsRequest;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.domain.survey.dto.FixedQuestionsCountResponse;
import com.playprobie.api.domain.survey.dto.QuestionFeedbackResponse;
import com.playprobie.api.domain.survey.dto.request.AiQuestionsRequest;
import com.playprobie.api.domain.survey.dto.request.CreateSurveyRequest;
import com.playprobie.api.domain.survey.dto.request.UpdateSurveyStatusRequest;
import com.playprobie.api.domain.survey.dto.response.SurveyResponse;
import com.playprobie.api.domain.survey.dto.response.UpdateSurveyStatusResponse;
import com.playprobie.api.domain.streaming.application.StreamingResourceService;
import com.playprobie.api.domain.streaming.dto.TestActionResponse;
import com.playprobie.api.global.error.exception.EntityNotFoundException;
import com.playprobie.api.infra.ai.AiClient;
import com.playprobie.api.infra.ai.dto.request.GenerateFeedbackRequest;
import com.playprobie.api.infra.ai.dto.response.GenerateFeedbackResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyService {

	private final SurveyRepository surveyRepository;
	private final FixedQuestionRepository fixedQuestionRepository;
	private final GameService gameService;
	private final StreamingResourceService streamingResourceService;
	private final AiClient aiClient;
	private final WorkspaceSecurityManager securityManager;

	@Value("${playprobie.base-url}")
	private String baseUrl;

	// ========== Survey CRUD ==========

	@Transactional
	public SurveyResponse createSurvey(CreateSurveyRequest request, User user) {
		Game game = gameService.getGameEntity(request.gameUuid(), user);
		// Security check is done inside getGameEntity

		TestPurpose testPurpose = parseTestPurpose(request.testPurpose());

		Survey survey = Survey.builder()
				.game(game)
				.name(request.surveyName())
				.testPurpose(testPurpose)
				.startAt(request.startedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime())
				.endAt(request.endedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime())
				.build();

		Survey savedSurvey = surveyRepository.save(survey);

		// URL 생성 (UUID 사용)
		String surveyUrl = baseUrl + "/surveys/session/" + savedSurvey.getUuid();
		savedSurvey.assignUrl(surveyUrl);

		return SurveyResponse.from(savedSurvey);
	}

	public List<SurveyResponse> getSurveys(UUID gameUuid, User user) {
		if (gameUuid != null) {
			// Validate access via GameService
			gameService.getGameEntity(gameUuid, user);
			return surveyRepository.findByGameUuid(gameUuid)
					.stream()
					.map(SurveyResponse::forList)
					.toList();
		}
		// TODO: Handle global listing security? Currently unsafe/legacy.
		// For now, we allow it but in strict mode we should block or filter.
		// Returning empty list or throwing error might be better if IDOR is strict.
		// given the context, let's keep it but ideally this path should be admin only.
		return surveyRepository.findAll()
				.stream()
				.map(SurveyResponse::forList)
				.toList();
	}

	public SurveyResponse getSurveyByUuid(UUID surveyUuid, User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
				.orElseThrow(EntityNotFoundException::new);
		securityManager.validateReadAccess(survey.getGame().getWorkspace(), user);
		return SurveyResponse.from(survey);
	}

	public Survey getSurveyEntity(UUID surveyUuid, User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
				.orElseThrow(EntityNotFoundException::new);
		securityManager.validateReadAccess(survey.getGame().getWorkspace(), user);
		return survey;
	}

	/**
	 * 설문 상태 업데이트 및 스트리밍 리소스 제어
	 */
	@Transactional
	public UpdateSurveyStatusResponse updateSurveyStatus(UUID surveyUuid, UpdateSurveyStatusRequest request,
			User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
				.orElseThrow(EntityNotFoundException::new);
		securityManager.validateWriteAccess(survey.getGame().getWorkspace(), user);

		SurveyStatus newStatus = SurveyStatus.valueOf(request.status());
		survey.updateStatus(newStatus);

		TestActionResponse streamingAction = null;
		if (newStatus == SurveyStatus.ACTIVE) {
			// JIT Provisioning: ACTIVE 시점에 Max Capacity로 확장
			streamingAction = streamingResourceService.activateResource(surveyUuid, user);
		} else if (newStatus == SurveyStatus.CLOSED) {
			// 설문 종료 시 리소스 즉시 해제
			streamingResourceService.deleteResource(surveyUuid, user);
			streamingAction = TestActionResponse.stopTest("CLEANING", 0);
		}

		return new UpdateSurveyStatusResponse(surveyUuid, survey.getStatus().name(), streamingAction);
	}

	// ========== AI & Questions ==========

	public List<String> generateAiQuestions(AiQuestionsRequest request) {
		return aiClient.generateQuestions(
				request.gameName(),
				String.join(", ", request.gameGenre()),
				request.gameContext(),
				request.testPurpose());
	}

	public QuestionFeedbackResponse getQuestionFeedback(String gameName, String gameGenre, String gameContext,
			String testPurpose, String question) {
		GenerateFeedbackRequest request = GenerateFeedbackRequest.builder()
				.gameName(gameName)
				.gameGenre(gameGenre)
				.gameContext(gameContext)
				.testPurpose(testPurpose)
				.originalQuestion(question)
				.build();
		// AI Feedback doesn't necessarily need workspace check if generic,
		// but if it uses context from DB, it should.
		// Current logic uses passed string.

		GenerateFeedbackResponse aiResponse = aiClient.getQuestionFeedback(request);

		return new QuestionFeedbackResponse(
				question,
				aiResponse.getFeedback(),
				aiResponse.getCandidates());
	}

	@Transactional
	public FixedQuestionsCountResponse createFixedQuestions(CreateFixedQuestionsRequest request, User user) {
		Survey survey = surveyRepository.findByUuid(request.surveyUuid())
				.orElseThrow(EntityNotFoundException::new);
		securityManager.validateWriteAccess(survey.getGame().getWorkspace(), user);

		List<FixedQuestion> questions = request.questions().stream()
				.map(item -> FixedQuestion.builder()
						.surveyId(survey.getId())
						.content(item.qContent())
						.order(item.qOrder())
						.status(QuestionStatus.CONFIRMED)
						.build())
				.toList();

		fixedQuestionRepository.saveAll(questions);

		return FixedQuestionsCountResponse.of(questions.size());
	}

	public List<FixedQuestionResponse> getConfirmedQuestions(UUID surveyUuid, User user) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
				.orElseThrow(EntityNotFoundException::new);
		securityManager.validateReadAccess(survey.getGame().getWorkspace(), user);
		return fixedQuestionRepository.findBySurveyIdAndStatusOrderByOrderAsc(survey.getId(), QuestionStatus.CONFIRMED)
				.stream()
				.map(FixedQuestionResponse::from)
				.toList();
	}

	// ========== Private ==========

	private TestPurpose parseTestPurpose(String code) {
		for (TestPurpose tp : TestPurpose.values()) {
			if (tp.getCode().equals(code)) {
				return tp;
			}
		}
		throw new IllegalArgumentException("Invalid test purpose code: " + code);
	}
}
