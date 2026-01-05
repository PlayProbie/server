package com.playprobie.api.domain.survey.application;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.application.GameService;
import com.playprobie.api.domain.game.domain.Game;
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

	@Value("${playprobie.base-url}")
	private String baseUrl;

	// ========== Survey CRUD ==========

	@Transactional
	public SurveyResponse createSurvey(CreateSurveyRequest request) {
		Game game = gameService.getGameEntity(request.gameUuid());
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

	public List<SurveyResponse> getSurveys(UUID gameUuid) {
		if (gameUuid != null) {
			return surveyRepository.findByGameUuid(gameUuid)
					.stream()
					.map(SurveyResponse::forList)
					.toList();
		}
		return surveyRepository.findAll()
				.stream()
				.map(SurveyResponse::forList)
				.toList();
	}

	public SurveyResponse getSurveyByUuid(UUID surveyUuid) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
				.orElseThrow(EntityNotFoundException::new);
		return SurveyResponse.from(survey);
	}

	public Survey getSurveyEntity(UUID surveyUuid) {
		return surveyRepository.findByUuid(surveyUuid)
				.orElseThrow(EntityNotFoundException::new);
	}

	/**
	 * 설문 상태 업데이트 및 스트리밍 리소스 제어
	 */
	@Transactional
	public UpdateSurveyStatusResponse updateSurveyStatus(UUID surveyUuid, UpdateSurveyStatusRequest request) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
				.orElseThrow(EntityNotFoundException::new);

		SurveyStatus newStatus = SurveyStatus.valueOf(request.status());
		survey.updateStatus(newStatus);

		TestActionResponse streamingAction = null;
		if (newStatus == SurveyStatus.ACTIVE) {
			// JIT Provisioning: ACTIVE 시점에 Max Capacity로 확장
			streamingAction = streamingResourceService.activateResource(surveyUuid);
		} else if (newStatus == SurveyStatus.CLOSED) {
			// 설문 종료 시 리소스 즉시 해제
			streamingResourceService.deleteResource(surveyUuid);
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

		GenerateFeedbackResponse aiResponse = aiClient.getQuestionFeedback(request);

		return new QuestionFeedbackResponse(
				question,
				aiResponse.getFeedback(),
				aiResponse.getCandidates());
	}

	@Transactional
	public FixedQuestionsCountResponse createFixedQuestions(CreateFixedQuestionsRequest request) {
		Survey survey = surveyRepository.findByUuid(request.surveyUuid())
				.orElseThrow(EntityNotFoundException::new);

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

	public List<FixedQuestionResponse> getConfirmedQuestions(UUID surveyUuid) {
		Survey survey = surveyRepository.findByUuid(surveyUuid)
				.orElseThrow(EntityNotFoundException::new);
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
