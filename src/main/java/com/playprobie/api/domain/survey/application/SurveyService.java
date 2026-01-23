package com.playprobie.api.domain.survey.application;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playprobie.api.domain.game.application.GameService;
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.streaming.application.StreamingResourceManager;
import com.playprobie.api.domain.streaming.application.StreamingTestManager;
import com.playprobie.api.domain.streaming.dto.TestActionResponse;
import com.playprobie.api.domain.survey.dao.FixedQuestionRepository;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.FixedQuestion;
import com.playprobie.api.domain.survey.domain.QuestionStatus;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.domain.SurveyStatus;
import com.playprobie.api.domain.survey.domain.TestStage;
import com.playprobie.api.domain.survey.dto.CreateFixedQuestionsRequest;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.domain.survey.dto.FixedQuestionsCountResponse;
import com.playprobie.api.domain.survey.dto.QuestionFeedbackRequest;
import com.playprobie.api.domain.survey.dto.QuestionFeedbackResponse;
import com.playprobie.api.domain.survey.dto.request.AiQuestionsRequest;
import com.playprobie.api.domain.survey.dto.request.CreateSurveyRequest;
import com.playprobie.api.domain.survey.dto.request.UpdateSurveyStatusRequest;
import com.playprobie.api.domain.survey.dto.response.SurveyResponse;
import com.playprobie.api.domain.survey.dto.response.UpdateSurveyStatusResponse;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.application.WorkspaceSecurityManager;
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
	private final GameRepository gameRepository;
	private final GameService gameService;
	private final StreamingTestManager streamingTestManager;
	private final StreamingResourceManager streamingResourceManager;
	private final AiClient aiClient;
	private final WorkspaceSecurityManager securityManager;
	private final ObjectMapper objectMapper;

	// ========== Survey CRUD ==========

	@Transactional
	public SurveyResponse createSurvey(CreateSurveyRequest request, User user) {
		Game game = gameService.getGameEntity(request.gameUuid(), user);
		// Security check is done inside getGameEntity

		TestStage testStage = parseTestStage(request.testStage());

		Survey survey = Survey.builder()
			.game(game)
			.name(request.surveyName())
			.startAt(request.startedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime())
			.endAt(request.endedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime())
			// 신규 필드 (feat/#47)
			.testStage(testStage)
			.themePriorities(request.themePriorities())
			.themeDetails(request.themeDetails())
			.versionNote(request.versionNote())
			.build();

		Survey savedSurvey = surveyRepository.save(survey);
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
			// 리소스가 없으면 null 반환 (Safe Method)
			streamingAction = streamingTestManager.activateResourceIfPresent(surveyUuid, user);

			if (streamingAction == null) {
				log.info("설문 활성화 완료 (스트리밍 리소스 없음). surveyUuid={}", surveyUuid);
			}
		} else if (newStatus == SurveyStatus.CLOSED) {
			// 설문 종료 시 리소스 즉시 해제
			// 리소스가 없으면 무시 (Safe Method)
			streamingResourceManager.deleteResourceIfPresent(surveyUuid, user);
			streamingAction = TestActionResponse.stopTest("CLEANING", 0);
		}

		return new UpdateSurveyStatusResponse(surveyUuid, survey.getStatus().name(), streamingAction);
	}

	// ========== AI & Questions ==========

	public List<String> generateAiQuestions(AiQuestionsRequest request, User user) {
		log.info("📢 AI 질문 생성 요청: gameUuid={}, elementsProvided={}", request.gameUuid(),
			request.extractedElements() != null);

		// 1. Check extractedElements
		java.util.Map<String, String> extractedElements = request.extractedElements();

		if (extractedElements == null || extractedElements.isEmpty()) {
			// Try Fetch from DB if gameUuid is present
			if (request.gameUuid() != null) {
				try {
					Game game = gameService.getGameEntity(request.gameUuid(), user);
					String json = game.getExtractedElements();
					if (json != null && !json.isBlank()) {
						extractedElements = objectMapper.readValue(json,
							new TypeReference<java.util.Map<String, String>>() {});
						log.info("🧩 DB에서 게임 요소 로드: {}", extractedElements);
					}
				} catch (Exception e) {
					log.warn("⚠️ 게임 요소 DB 조회 실패 (계속 진행): {}", e.getMessage());
				}
			} else if (request.gameName() != null) {
				// Fallback: Search by Game Name (Workaround for client missing UUID)
				try {
					log.info("⚠️ gameUuid 부재로 인해 gameName으로 조회 시도: {}", request.gameName());
					// 중복된 이름이 있을 수 있으므로 모두 조회하여 가장 최신의 유효한 데이터를 사용
					List<Game> games = gameRepository.findAllByName(request.gameName());

					// ID 내림차순(최신순)으로 정렬 후 extractedElements가 있는 첫 번째 게임 선택
					Game game = games.stream()
						.sorted((g1, g2) -> g2.getId().compareTo(g1.getId()))
						.filter(g -> g.getExtractedElements() != null && !g.getExtractedElements().isBlank())
						.findFirst()
						.orElse(null);

					if (game != null) {
						String json = game.getExtractedElements();
						extractedElements = objectMapper.readValue(json,
							new TypeReference<java.util.Map<String, String>>() {});
						log.info("🧩 [Fallback] DB에서 게임 요소 로드 (by name, found {} games): {}", games.size(),
							extractedElements);
					} else {
						log.warn("⚠️ 이름으로 게임들을 찾았으나({}) 유효한 extractedElements가 없음", games.size());
					}
				} catch (Exception e) {
					log.warn("⚠️ 게임 요소 DB 조회 실패 (Fallback): {}", e.getMessage());
				}
			}
			// Fallback: AI Auto-Extraction if still empty
			if (extractedElements == null || extractedElements.isEmpty()) {
				log.info("🧩 게임 요소 정보 부재. AI 자동 추출 시작: {}", request.gameName());
			}
		}
		// Flatten themeDetails values for purposeSubcategories
		List<String> purposeSubcategories = request.themeDetails() != null
			? request.themeDetails().values().stream()
				.flatMap(List::stream)
				.toList()
			: List.of();

		// Default count to 5 if null
		int count = request.count() != null ? request.count() : 5;

		com.playprobie.api.infra.ai.dto.request.QuestionRecommendRequest recommendRequest = com.playprobie.api.infra.ai.dto.request.QuestionRecommendRequest
			.builder()
			.gameName(request.gameName())
			.gameDescription(request.gameContext()) // context -> description
			.genres(request.gameGenre())
			.testPhase(request.testStage() != null ? request.testStage() : "prototype")
			.purposeCategories(request.themePriorities())
			.purposeSubcategories(purposeSubcategories)
			.extractedElements(filterNullValues(extractedElements))
			.topK(count) // Use requested count
			.shuffle(request.shuffle())
			.build();

		com.playprobie.api.infra.ai.dto.response.QuestionRecommendResponse response = aiClient
			.recommendQuestions(recommendRequest);

		// Map Response DTOs to List<String> with slot replacement
		java.util.Map<String, String> elementsToUse = extractedElements != null ? extractedElements
			: java.util.Map.of();

		return response.questions().stream()
			.limit(count)
			.map(q -> replaceSlots(q.text(), elementsToUse))
			.toList();
	}

	public QuestionFeedbackResponse getQuestionFeedback(
		QuestionFeedbackRequest request) {

		// Data extraction and processing logic moved from controller
		String question = request.questions().get(0);

		GenerateFeedbackRequest aiRequest = GenerateFeedbackRequest.builder()
			.gameName(request.gameName())
			.gameGenre(String.join(", ", request.gameGenre()))
			.gameContext(request.gameContext())
			.themePriorities(request.themePriorities())
			.themeDetails(request.themeDetails())
			.originalQuestion(question)
			.build();

		GenerateFeedbackResponse aiResponse = aiClient.getQuestionFeedback(aiRequest);

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

	private TestStage parseTestStage(String code) {
		if (code == null)
			return null;
		for (TestStage ts : TestStage.values()) {
			if (ts.getCode().equals(code)) {
				return ts;
			}
		}
		throw new IllegalArgumentException("Invalid test stage code: " + code);
	}

	private java.util.Map<String, String> filterNullValues(java.util.Map<String, String> elements) {
		if (elements == null) {
			return java.util.Collections.emptyMap();
		}
		return elements.entrySet().stream()
			.filter(entry -> entry.getValue() != null)
			.collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));
	}

	private String replaceSlots(String text, java.util.Map<String, String> elements) {
		if (text == null || elements == null || elements.isEmpty()) {
			return text;
		}
		String result = text;
		for (java.util.Map.Entry<String, String> entry : elements.entrySet()) {
			String key = "[" + entry.getKey() + "]";
			if (result.contains(key)) {
				log.info("🔄 슬롯 치환: {} -> {}", key, entry.getValue());
				result = result.replace(key, entry.getValue());
			}
		}
		return result;
	}

}
