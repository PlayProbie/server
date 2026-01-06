package com.playprobie.api.domain.survey.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.application.GameService;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.survey.dao.FixedQuestionRepository;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.FixedQuestion;
import com.playprobie.api.domain.survey.domain.QuestionStatus;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.domain.TestPurpose;
import com.playprobie.api.domain.survey.domain.TestStage;
import com.playprobie.api.domain.survey.dto.CreateFixedQuestionsRequest;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.domain.survey.dto.FixedQuestionsCountResponse;
import com.playprobie.api.domain.survey.dto.QuestionFeedbackResponse;
import com.playprobie.api.domain.survey.dto.request.AiQuestionsRequest;
import com.playprobie.api.domain.survey.dto.request.CreateSurveyRequest;
import com.playprobie.api.domain.survey.dto.response.SurveyResponse;
import com.playprobie.api.global.error.exception.EntityNotFoundException;
import com.playprobie.api.infra.ai.AiClient;
import com.playprobie.api.infra.ai.dto.request.GenerateFeedbackRequest;
import com.playprobie.api.infra.ai.dto.response.GenerateFeedbackResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyService {

	private final SurveyRepository surveyRepository;
	private final FixedQuestionRepository fixedQuestionRepository;
	private final GameService gameService;
	private final AiClient aiClient;

	// ========== Survey CRUD ==========

	@org.springframework.beans.factory.annotation.Value("${playprobie.base-url}")
	private String baseUrl;

	@Transactional
	public SurveyResponse createSurvey(CreateSurveyRequest request) {
		Game game = gameService.getGameEntity(request.gameId());
		TestPurpose testPurpose = parseTestPurpose(request.testPurpose());
		TestStage testStage = parseTestStage(request.testStage());

		// themeDetails 검증: 키가 themePriorities에 포함되어야 함
		if (request.themeDetails() != null && !request.themeDetails().isEmpty()) {
			for (String key : request.themeDetails().keySet()) {
				if (!request.themePriorities().contains(key)) {
					throw new IllegalArgumentException(
							"theme_details의 키('" + key + "')는 theme_priorities에 포함되어야 합니다");
				}
			}
		}

		Survey survey = Survey.builder()
				.game(game)
				.name(request.surveyName())
				.testPurpose(testPurpose)
				.startAt(request.startedAt().atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime())
				.endAt(request.endedAt().atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime())
				// 신규 필드
				.testStage(testStage)
				.themePriorities(request.themePriorities())
				.themeDetails(request.themeDetails())
				.versionNote(request.versionNote())
				.build();

		Survey savedSurvey = surveyRepository.save(survey);

		// URL 생성 (UUID 사용)
		String surveyUrl = baseUrl + "/surveys/session/" + savedSurvey.getUuid();
		savedSurvey.assignUrl(surveyUrl);

		return SurveyResponse.from(savedSurvey);
	}

	public SurveyResponse getSurvey(Long surveyId) {
		Survey survey = surveyRepository.findById(surveyId)
				.orElseThrow(EntityNotFoundException::new);
		return SurveyResponse.from(survey);
	}

	/**
	 * 전체 설문 목록 조회
	 * GET /surveys
	 */
	public List<SurveyResponse> getAllSurveys() {
		List<Survey> surveys = surveyRepository.findAll();
		return surveys.stream()
				.map(SurveyResponse::from)
				.toList();
	}

	public Survey getSurveyEntity(Long surveyId) {
		return surveyRepository.findById(surveyId)
				.orElseThrow(EntityNotFoundException::new);
	}

	/**
	 * AI를 통해 질문 생성 (DB 저장 없이 미리보기)
	 * POST /surveys/ai-questions
	 */
	public List<String> generateAiQuestions(AiQuestionsRequest request) {
		return aiClient.generateQuestions(
				request.gameName(),
				String.join(", ", request.gameGenre()),
				request.gameContext(),
				request.testPurpose());
	}

	/**
	 * 질문에 대한 피드백 제공
	 * POST /surveys/question-feedback
	 *
	 * Note: FastAPI는 단일 질문에 대해 피드백을 받으므로, 각 질문에 대해 순차 호출
	 */
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

	/**
	 * 고정 질문 저장
	 * POST /surveys/fixed_questions
	 */
	@Transactional
	public FixedQuestionsCountResponse createFixedQuestions(CreateFixedQuestionsRequest request) {
		// 설문 존재 확인
		if (!surveyRepository.existsById(request.surveyId())) {
			throw new EntityNotFoundException();
		}

		List<FixedQuestion> questions = request.questions().stream()
				.map(item -> FixedQuestion.builder()
						.surveyId(request.surveyId())
						.content(item.qContent())
						.order(item.qOrder())
						.status(QuestionStatus.CONFIRMED)
						.build())
				.toList();

		fixedQuestionRepository.saveAll(questions);

		return FixedQuestionsCountResponse.of(questions.size());
	}

	// ========== 확정 질문 조회 ==========

	/**
	 * 확정(CONFIRMED) 질문 목록 조회
	 * GET /surveys/{surveyId}/questions
	 */
	public List<FixedQuestionResponse> getConfirmedQuestions(Long surveyId) {
		if (!surveyRepository.existsById(surveyId)) {
			throw new EntityNotFoundException();
		}
		return fixedQuestionRepository.findBySurveyIdAndStatusOrderByOrderAsc(surveyId, QuestionStatus.CONFIRMED)
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

	private TestStage parseTestStage(String code) {
		for (TestStage ts : TestStage.values()) {
			if (ts.getCode().equals(code) || ts.name().equals(code)) {
				return ts;
			}
		}
		throw new IllegalArgumentException("Invalid test stage code: " + code);
	}
}
