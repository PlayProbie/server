package com.playprobie.api.global.config;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playprobie.api.domain.analytics.application.AnalyticsService;
import com.playprobie.api.domain.analytics.dao.QuestionResponseAnalysisRepository;
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameGenre;
import com.playprobie.api.domain.interview.dao.InterviewLogRepository;
import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.domain.QuestionType;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.interview.domain.TesterProfile;
import com.playprobie.api.domain.survey.dao.FixedQuestionRepository;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.domain.survey.domain.FixedQuestion;
import com.playprobie.api.domain.survey.domain.QuestionStatus;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.domain.TestPurpose;
import com.playprobie.api.domain.survey.domain.TestStage;
import com.playprobie.api.domain.user.dao.UserRepository;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.dao.WorkspaceMemberRepository;
import com.playprobie.api.domain.workspace.dao.WorkspaceRepository;
import com.playprobie.api.domain.workspace.domain.Workspace;
import com.playprobie.api.domain.workspace.domain.WorkspaceMember;
import com.playprobie.api.domain.workspace.domain.WorkspaceRole;
import com.playprobie.api.infra.ai.AiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mock 데이터 로더
 *
 * <p>
 * 역할:
 * </p>
 * <ul>
 * <li>애플리케이션 시작 시 mock_data.json 파일을 읽어 초기 데이터 생성</li>
 * <li>User → Workspace → Game → Survey → FixedQuestion → Session → InterviewLog
 * 순서로 데이터 로딩</li>
 * <li>완료된 세션에 대해 AI Embedding 및 Analytics 자동 실행</li>
 * </ul>
 *
 * <p>
 * JSON 파일 위치: {@code src/main/resources/data/mock_data.json}
 * </p>
 *
 * <p>
 * JSON 구조 예시:
 * </p>
 *
 * <pre>{@code
 * {
 *   "game": {
 *     "name": "게임 이름",
 *     "genres": ["RPG", "ACTION"],  // GameGenre Enum 값
 *     "description": "게임 상세 설명"
 *   },
 *   "survey": {
 *     "name": "설문 이름",
 *     "testPurpose": "GAMEPLAY_VALIDATION",  // TestPurpose Enum 값
 *     "testStage": "PLAYTEST",  // TestStage Enum 값 (optional)
 *     "themePriorities": ["GAMEPLAY", "UI_UX"],  // 테스트 테마 우선순위 (1-3개)
 *     "themeDetails": {  // 테마별 세부 키워드 (optional)
 *       "GAMEPLAY": ["조작감", "난이도", "밸런스"],
 *       "UI_UX": ["HUD", "메뉴", "튜토리얼"]
 *     },
 *     "versionNote": "버전 노트 (optional)",
 *     "questions": [
 *       {
 *         "id": 1,
 *         "content": "질문 내용",
 *         "order": 1
 *       }
 *     ]
 *   },
 *   "sessions": [
 *     {
 *       "id": 1,
 *       "profile": {
 *         "ageGroup": "20s",  // 테스터 연령대
 *         "gender": "MALE",   // 테스터 성별
 *         "preferGenre": "RPG"  // 선호 장르
 *       },
 *       "logs": [
 *         {
 *           "fixedQuestionId": 1,
 *           "turnNum": 1,
 *           "type": "FIXED",  // QuestionType: FIXED 또는 TAIL
 *           "questionText": "질문 텍스트",
 *           "answerText": "답변 텍스트"
 *         }
 *       ]
 *     }
 *   ]
 * }
 * }</pre>
 */
@Component
@Profile({"local", "dev", "prod"})
@RequiredArgsConstructor
@Slf4j
public class MockDataLoader implements CommandLineRunner {

	private final GameRepository gameRepository;
	private final SurveyRepository surveyRepository;
	private final FixedQuestionRepository fixedQuestionRepository;
	private final SurveySessionRepository surveySessionRepository;
	private final InterviewLogRepository interviewLogRepository;
	private final ObjectMapper objectMapper;
	private final AiClient aiClient;
	private final QuestionResponseAnalysisRepository analysisRepository;
	private final UserRepository userRepository;
	private final WorkspaceRepository workspaceRepository;
	private final WorkspaceMemberRepository workspaceMemberRepository;
	private final PasswordEncoder passwordEncoder;
	private final AnalyticsService analyticsService;

	/**
	 * 설문 설정 (내부 클래스)
	 */
	@lombok.Data
	@lombok.AllArgsConstructor
	private static class SurveyConfig {
		String name;
		String jsonFileName;
	}

	@Override
	public void run(String... args) throws Exception {
		if (surveyRepository.count() > 0) {
			log.info("⏩ 데이터가 이미 존재합니다. Mock 데이터 로딩을 건너뜁니다.");

			// Analytics도 이미 존재하는지 확인
			if (analysisRepository.count() > 0) {
				log.info("⏩ Analytics 데이터도 이미 존재합니다. AI 처리를 건너뜁니다.");
				return;
			} else {
				log.info("🔄 Analytics 데이터가 없습니다. AI 처리를 시작합니다...");
				triggerAiProcessingForExistingSurveys();
			}
			return;
		}

		log.info("🚀 Mock 데이터 로딩 시작...");
		log.info("========================================");

		// 4개 설문 설정 (모두 100개 세션)
		java.util.List<SurveyConfig> surveyConfigs = java.util.List.of(
			new SurveyConfig("v1.0.0 플레이테스트", "/data/mock_data_2_100.json"),
			new SurveyConfig("v1.1.0 플레이테스트", "/data/mock_data_2_100.json"),
			new SurveyConfig("v2.0.0 플레이테스트", "/data/mock_data_2_100.json"),
			new SurveyConfig("v2.1.0 플레이테스트", "/data/mock_data_2_100.json"));

		// Demo User & Workspace는 한 번만 생성
		User demoUser = createDemoUser();
		Workspace workspace = createDemoWorkspace(demoUser);
		Game game = null;

		// 설문별 순차 처리
		for (int i = 0; i < surveyConfigs.size(); i++) {
			SurveyConfig config = surveyConfigs.get(i);
			int surveyIndex = i + 1;

			log.info("\n========================================");
			log.info("📋 [{}/{}] Survey 처리 시작: {}", surveyIndex, surveyConfigs.size(), config.getName());
			log.info("========================================");

			try {
				// 1️⃣ 데이터 생성 (Survey + Questions + Sessions)
				log.info("🔄 [{}/{}] 데이터 생성 중...", surveyIndex, surveyConfigs.size());
				Survey survey = loadSurveyDataWithTransaction(config, workspace, game);

				// 첫 번째 설문에서 생성된 게임 재사용
				if (game == null) {
					game = survey.getGame();
				}

				log.info("✅ [{}/{}] 데이터 생성 완료: Survey ID={}, Sessions={}",
					surveyIndex, surveyConfigs.size(), survey.getId(),
					surveySessionRepository.countBySurveyIdAndStatus(survey.getId(),
						com.playprobie.api.domain.interview.domain.SessionStatus.COMPLETED));

				// 2️⃣ AI Embedding
				log.info("🔄 [{}/{}] AI Embedding 시작...", surveyIndex, surveyConfigs.size());
				embedSurveyData(survey);
				log.info("✅ [{}/{}] AI Embedding 완료", surveyIndex, surveyConfigs.size());

				// 3️⃣ Analytics 수행
				log.info("🔄 [{}/{}] Analytics 시작...", surveyIndex, surveyConfigs.size());
				analyzeSurveyQuestions(survey);
				log.info("✅ [{}/{}] Analytics 완료", surveyIndex, surveyConfigs.size());

				// 4️⃣ Survey Summary 생성
				log.info("🔄 [{}/{}] Survey Summary 생성 중...", surveyIndex, surveyConfigs.size());
				generateAndSaveSurveySummary(survey);
				log.info("✅ [{}/{}] Survey Summary 완료", surveyIndex, surveyConfigs.size());

				// 5️⃣ 완료 검증
				verifySurveyPipelineCompleted(survey);

				log.info("\n✅✅✅ [{}/{}] Survey 완전 처리 완료: {} ✅✅✅",
					surveyIndex, surveyConfigs.size(), config.getName());

			} catch (Exception e) {
				log.error("❌ [{}/{}] Survey 처리 실패: {}", surveyIndex, surveyConfigs.size(),
					config.getName(), e);
				// 개별 설문 실패 시 다음 설문 계속 처리 (앱 종료 방지)
			}
		}

		log.info("\n========================================");
		log.info("🎉 모든 Survey 처리 완료!");
		log.info("========================================");
	}

	/**
	 * Demo User 생성
	 */
	private User createDemoUser() {
		User demoUser = userRepository.save(User.builder()
			.email("jungle@playprobie.com")
			.password(passwordEncoder.encode("jungle1234"))
			.name("Jungle")
			.build());
		log.info("💾 Demo User 생성 완료: ID={}, email={}", demoUser.getId(), demoUser.getEmail());
		return demoUser;
	}

	/**
	 * Demo Workspace 생성
	 */
	private Workspace createDemoWorkspace(User demoUser) {
		Workspace workspace = workspaceRepository.save(Workspace.builder()
			.uuid(java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")) // Demo용 고정 UUID
			.name("Jungle Workspace")
			.description("Jungle 11기 나만의 무기 만들기")
			.build());

		workspaceMemberRepository.save(WorkspaceMember.builder()
			.workspace(workspace)
			.user(demoUser)
			.role(WorkspaceRole.OWNER)
			.build());

		log.info("💾 Workspace 생성 완료: ID={}, Name={}, UUID={}",
			workspace.getId(), workspace.getName(), workspace.getUuid());
		return workspace;
	}

	/**
	 * 설문 데이터 생성 (Survey + Questions + Sessions)
	 */
	@Transactional
	protected Survey loadSurveyDataWithTransaction(SurveyConfig config, Workspace workspace, Game existingGame)
		throws Exception {
		try (InputStream inputStream = getClass().getResourceAsStream(config.getJsonFileName())) {
			if (inputStream == null) {
				throw new IllegalStateException("JSON 파일을 찾을 수 없습니다: " + config.getJsonFileName());
			}

			Map<String, Object> data = objectMapper.readValue(inputStream, new TypeReference<>() {});

			// Game 생성 (첫 번째 설문에서만)
			Game game = existingGame;
			if (game == null) {
				Map<String, Object> gameData = objectMapper.convertValue(data.get("game"),
					new TypeReference<Map<String, Object>>() {});

				List<String> genreStrings = objectMapper.convertValue(gameData.get("genres"),
					new TypeReference<List<String>>() {});
				List<GameGenre> genres = genreStrings.stream()
					.map(GameGenre::valueOf)
					.collect(Collectors.toList());

				game = gameRepository.save(Game.builder()
					.workspace(workspace)
					.name((String)gameData.get("name"))
					.genres(genres)
					.context((String)gameData.get("description"))
					.extractedElements(
						"{\"core_mechanic\": \"카트 레이싱\", \"player_goal\": \"레이스 우승\", \"racing_element\": \"아케이드 레이싱\"}")
					.build());
				log.info("💾 Game 생성 완료: {}, UUID={}", game.getName(), game.getUuid());
			}

			// Survey 생성
			Map<String, Object> surveyData = objectMapper.convertValue(data.get("survey"),
				new TypeReference<Map<String, Object>>() {});

			TestPurpose testPurpose = TestPurpose.valueOf((String)surveyData.get("testPurpose"));
			TestStage testStage = surveyData.get("testStage") != null
				? TestStage.valueOf((String)surveyData.get("testStage"))
				: null;

			List<String> themePriorities = objectMapper.convertValue(surveyData.get("themePriorities"),
				new TypeReference<List<String>>() {});

			Map<String, List<String>> themeDetails = objectMapper.convertValue(surveyData.get("themeDetails"),
				new TypeReference<Map<String, List<String>>>() {});

			String versionNote = (String)surveyData.get("versionNote");

			Survey survey = surveyRepository.saveAndFlush(Survey.builder()
				.game(game)
				.name(config.getName()) // 설문 이름을 config에서 가져옴
				.testPurpose(testPurpose)
				.testStage(testStage)
				.themePriorities(themePriorities)
				.themeDetails(themeDetails)
				.versionNote(versionNote)
				.startAt(LocalDateTime.now().minusDays(7))
				.endAt(LocalDateTime.now().plusDays(7))
				.build());

			if (survey.getId() == null) {
				throw new IllegalStateException("Survey 저장 실패: " + config.getName());
			}

			log.info("💾 Survey 저장 완료: ID={}, Name={}", survey.getId(), survey.getName());

			// FixedQuestion 생성 및 JSON ID → DB ID 매핑 생성
			List<Map<String, Object>> questionsData = objectMapper.convertValue(surveyData.get("questions"),
				new TypeReference<List<Map<String, Object>>>() {});
			Map<Long, Long> questionIdMapping = new java.util.HashMap<>(); // JSON id → DB id
			for (Map<String, Object> qData : questionsData) {
				Long jsonId = ((Number)qData.get("id")).longValue();
				FixedQuestion savedQuestion = fixedQuestionRepository.save(FixedQuestion.builder()
					.surveyId(survey.getId())
					.content((String)qData.get("content"))
					.order((Integer)qData.get("order"))
					.status(QuestionStatus.CONFIRMED)
					.build());
				questionIdMapping.put(jsonId, savedQuestion.getId());
			}
			fixedQuestionRepository.flush();
			log.info("💾 FixedQuestion {}개 저장 완료 (ID 매핑: {})", questionsData.size(), questionIdMapping);

			// Session & Logs 생성
			List<Map<String, Object>> sessionsData = objectMapper.convertValue(data.get("sessions"),
				new TypeReference<List<Map<String, Object>>>() {});
			int logCount = 0;

			for (Map<String, Object> sData : sessionsData) {
				Map<String, Object> profileData = objectMapper.convertValue(sData.get("profile"),
					new TypeReference<Map<String, Object>>() {});

				TesterProfile testerProfile = TesterProfile.builder()
					.testerId((String)profileData.get("testerId"))
					.ageGroup((String)profileData.get("ageGroup"))
					.gender((String)profileData.get("gender"))
					.preferGenre((String)profileData.get("preferGenre"))
					.build();

				SurveySession session = SurveySession.builder()
					.survey(survey)
					.testerProfile(testerProfile)
					.build();
				session.complete();
				surveySessionRepository.save(session);

				List<Map<String, Object>> logsData = objectMapper.convertValue(sData.get("logs"),
					new TypeReference<List<Map<String, Object>>>() {});
				for (Map<String, Object> lData : logsData) {
					Long jsonFixedQuestionId = ((Number)lData.get("fixedQuestionId")).longValue();
					// JSON ID를 실제 DB ID로 변환
					Long actualFixedQuestionId = questionIdMapping.get(jsonFixedQuestionId);
					if (actualFixedQuestionId == null) {
						log.warn("⚠️ 매핑되지 않은 fixedQuestionId: {}", jsonFixedQuestionId);
						continue;
					}

					interviewLogRepository.save(InterviewLog.builder()
						.session(session)
						.fixedQuestionId(actualFixedQuestionId)
						.turnNum((Integer)lData.get("turnNum"))
						.type(QuestionType.valueOf((String)lData.get("type")))
						.questionText((String)lData.get("questionText"))
						.answerText((String)lData.get("answerText"))
						.build());
					logCount++;
				}
			}
			surveySessionRepository.flush();
			interviewLogRepository.flush();
			log.info("💾 SurveySession {}개, InterviewLog {}개 저장 완료", sessionsData.size(), logCount);

			return survey;
		}
	}

	/**
	 * AI Embedding 처리 (해당 설문의 모든 세션)
	 */
	private void embedSurveyData(Survey survey) throws InterruptedException {
		// AI 서버 상태 확인
		waitForAiServer();

		List<SurveySession> completedSessions = surveySessionRepository.findAll()
			.stream()
			.filter(s -> s.getSurvey().getId().equals(survey.getId()))
			.filter(s -> s.getStatus() == com.playprobie.api.domain.interview.domain.SessionStatus.COMPLETED)
			.collect(Collectors.toList());

		if (completedSessions.isEmpty()) {
			log.warn("완료된 세션이 없습니다.");
			return;
		}

		String surveyUuid = survey.getUuid().toString();
		log.info("🚀 AI Embedding 시작 (총 {}개 세션)", completedSessions.size());

		final int BATCH_SIZE = 10;
		final int CONCURRENCY_LIMIT = 50;

		java.util.concurrent.atomic.AtomicInteger totalCompletedEmbeddings = new java.util.concurrent.atomic.AtomicInteger(
			0);
		java.util.concurrent.atomic.AtomicInteger totalFailedEmbeddings = new java.util.concurrent.atomic.AtomicInteger(
			0);

		int totalBatches = (int)Math.ceil((double)completedSessions.size() / BATCH_SIZE);

		for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
			final int currentBatchIndex = batchIndex;
			int startIdx = currentBatchIndex * BATCH_SIZE;
			int endIdx = Math.min(startIdx + BATCH_SIZE, completedSessions.size());
			List<SurveySession> batchSessions = completedSessions.subList(startIdx, endIdx);

			List<reactor.core.publisher.Mono<Void>> batchTasks = new java.util.ArrayList<>();

			for (SurveySession session : batchSessions) {
				String sessionId = session.getUuid().toString();

				Map<Long, List<InterviewLog>> logsByFixedQuestion = interviewLogRepository
					.findBySessionIdOrderByTurnNumAsc(session.getId())
					.stream()
					.collect(Collectors.groupingBy(InterviewLog::getFixedQuestionId));

				for (Map.Entry<Long, List<InterviewLog>> entry : logsByFixedQuestion.entrySet()) {
					Long fixedQuestionId = entry.getKey();
					List<InterviewLog> logs = entry.getValue();

					List<com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest.QaPair> qaPairs = logs
						.stream()
						.filter(l -> l.getAnswerText() != null)
						.map(l -> com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest.QaPair
							.of(l.getQuestionText(), l.getAnswerText(), l.getType().name()))
						.collect(Collectors.toList());

					if (!qaPairs.isEmpty()) {
						Map<String, Object> metadata = new java.util.HashMap<>();
						if (session.getTesterProfile() != null) {
							TesterProfile profile = session.getTesterProfile();
							if (profile.getGender() != null)
								metadata.put("gender", profile.getGender());
							if (profile.getAgeGroup() != null)
								metadata.put("age_group", profile.getAgeGroup());
							if (profile.getPreferGenre() != null)
								metadata.put("prefer_genre", profile.getPreferGenre());
						}

						com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest request = com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest
							.builder()
							.sessionId(sessionId)
							.surveyUuid(surveyUuid)
							.fixedQuestionId(fixedQuestionId)
							.qaPairs(qaPairs)
							.metadata(metadata)
							.autoTriggerAnalysis(false)
							.build();

						reactor.core.publisher.Mono<Void> task = aiClient
							.embedSessionData(request)
							.doOnSuccess(result -> totalCompletedEmbeddings.incrementAndGet())
							.doOnError(error -> totalFailedEmbeddings.incrementAndGet())
							.onErrorResume(e -> reactor.core.publisher.Mono.empty())
							.then();

						batchTasks.add(task);
					}
				}
			}

			reactor.core.publisher.Flux.fromIterable(batchTasks)
				.flatMap(mono -> mono.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()),
					CONCURRENCY_LIMIT)
				.blockLast(java.time.Duration.ofMinutes(5));
		}

		log.info("✅ Embedding 완료: 성공 {}, 실패 {}", totalCompletedEmbeddings.get(),
			totalFailedEmbeddings.get());
	}

	/**
	 * Analytics 수행 (해당 설문의 모든 질문)
	 */
	private void analyzeSurveyQuestions(Survey survey) {
		List<FixedQuestion> questions = fixedQuestionRepository.findBySurveyIdOrderByOrderAsc(survey.getId());

		if (questions.isEmpty()) {
			log.warn("분석할 질문이 없습니다.");
			return;
		}

		java.util.UUID surveyUuid = survey.getUuid();
		log.info("🔍 Analytics 시작 (총 {}개 질문)", questions.size());

		java.util.concurrent.atomic.AtomicInteger totalCompletedAnalytics = new java.util.concurrent.atomic.AtomicInteger(
			0);
		java.util.concurrent.atomic.AtomicInteger totalFailedAnalytics = new java.util.concurrent.atomic.AtomicInteger(
			0);

		for (FixedQuestion question : questions) {
			try {
				analyticsService.analyzeSingleQuestion(surveyUuid, question.getId());
				totalCompletedAnalytics.incrementAndGet();
			} catch (Exception error) {
				totalFailedAnalytics.incrementAndGet();
				log.error("❌ Analytics 실패: questionId={}, error={}", question.getId(), error.getMessage());
			}
		}

		log.info("✅ Analytics 완료: 성공 {}, 실패 {}", totalCompletedAnalytics.get(),
			totalFailedAnalytics.get());
	}

	/**
	 * Survey Summary 생성 및 저장
	 */
	@Transactional
	protected void generateAndSaveSurveySummary(Survey survey) {
		try {
			List<String> metaSummaries = analysisRepository.findAllBySurveyId(survey.getId())
				.stream()
				.map(analysis -> {
					try {
						String json = analysis.getResultJson();
						if (json == null || json.isBlank())
							return null;
						com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
						if (node.has("meta_summary")) {
							return node.get("meta_summary").asText();
						}
					} catch (Exception e) {
						log.warn("meta_summary 추출 실패: {}", e.getMessage());
					}
					return null;
				})
				.filter(java.util.Objects::nonNull)
				.filter(s -> !s.isBlank())
				.collect(Collectors.toList());

			if (!metaSummaries.isEmpty()) {
				log.info("📝 meta_summary {}개 추출, Survey Summary 생성 중...", metaSummaries.size());
				String surveySummaryResult = aiClient.generateSurveySummary(metaSummaries)
					.block(java.time.Duration.ofMinutes(2));

				if (surveySummaryResult != null && !surveySummaryResult.isBlank()) {
					survey.updateSurveySummary(surveySummaryResult);
					surveyRepository.saveAndFlush(survey);
					log.info("✅ Survey Summary 저장 완료");
				}
			} else {
				log.warn("⚠️ meta_summary가 없어 Survey Summary를 건너뜁니다.");
			}
		} catch (Exception e) {
			log.error("❌ Survey Summary 생성 실패: {}", e.getMessage());
			throw new RuntimeException("Survey Summary 생성 실패", e);
		}
	}

	/**
	 * 설문 파이프라인 완료 검증
	 */
	private void verifySurveyPipelineCompleted(Survey survey) {
		// 1. Survey Summary 존재 확인
		Survey refreshedSurvey = surveyRepository.findById(survey.getId())
			.orElseThrow(() -> new IllegalStateException("Survey not found: " + survey.getId()));

		if (refreshedSurvey.getSurveySummary() == null || refreshedSurvey.getSurveySummary().isBlank()) {
			throw new IllegalStateException("Survey Summary 누락: " + survey.getName());
		}

		// 2. Analytics 결과 존재 확인
		long analysisCount = analysisRepository.findAllBySurveyId(survey.getId()).size();
		long questionCount = fixedQuestionRepository.countBySurveyId(survey.getId());

		if (analysisCount != questionCount) {
			throw new IllegalStateException(String.format(
				"Analytics 불완전: %s (expected=%d, actual=%d)",
				survey.getName(), questionCount, analysisCount));
		}

		log.info("✅ 파이프라인 검증 완료: Survey={}, Questions={}, Analytics={}",
			survey.getName(), questionCount, analysisCount);
	}

	/**
	 * AI 서버 연결 대기
	 */
	private void waitForAiServer() throws InterruptedException {
		log.info("⏳ AI 서버 연결 확인 중...");
		int maxRetries = 30;
		int retryCount = 0;

		while (retryCount < maxRetries) {
			if (aiClient.checkHealth()) {
				log.info("✅ AI 서버 연결 성공");
				return;
			}
			retryCount++;
			log.warn("⚠️ AI 서버 연결 실패. 30초 후 재시도... ({}/{})", retryCount, maxRetries);
			Thread.sleep(30000);
		}

		throw new IllegalStateException("AI 서버가 준비되지 않았습니다.");
	}

	/**
	 * 기존 설문들에 대해 AI 처리 수행 (데이터는 있지만 Analytics가 없는 경우)
	 */
	private void triggerAiProcessingForExistingSurveys() {
		try {
			List<Survey> allSurveys = surveyRepository.findAll();

			if (allSurveys.isEmpty()) {
				log.warn("⚠️ 처리할 설문이 없습니다.");
				return;
			}

			log.info("🚀 기존 설문 AI 처리 시작 (총 {}개 설문)", allSurveys.size());

			for (int i = 0; i < allSurveys.size(); i++) {
				Survey survey = allSurveys.get(i);
				int surveyIndex = i + 1;

				log.info("\n========================================");
				log.info("📋 [{}/{}] Survey AI 처리: {}", surveyIndex, allSurveys.size(), survey.getName());
				log.info("========================================");

				try {
					// 1️⃣ AI Embedding
					log.info("🔄 [{}/{}] AI Embedding 시작...", surveyIndex, allSurveys.size());
					embedSurveyData(survey);
					log.info("✅ [{}/{}] AI Embedding 완료", surveyIndex, allSurveys.size());

					// 2️⃣ Analytics 수행
					log.info("🔄 [{}/{}] Analytics 시작...", surveyIndex, allSurveys.size());
					analyzeSurveyQuestions(survey);
					log.info("✅ [{}/{}] Analytics 완료", surveyIndex, allSurveys.size());

					// 3️⃣ Survey Summary 생성
					log.info("🔄 [{}/{}] Survey Summary 생성 중...", surveyIndex, allSurveys.size());
					generateAndSaveSurveySummary(survey);
					log.info("✅ [{}/{}] Survey Summary 완료", surveyIndex, allSurveys.size());

					// 4️⃣ 완료 검증
					verifySurveyPipelineCompleted(survey);

					log.info("\n✅✅✅ [{}/{}] Survey AI 처리 완료: {} ✅✅✅",
						surveyIndex, allSurveys.size(), survey.getName());

				} catch (Exception e) {
					log.error("❌ [{}/{}] Survey AI 처리 실패: {}", surveyIndex, allSurveys.size(),
						survey.getName(), e);
					// 개별 설문 실패 시 다음 설문 계속 처리
				}
			}

			log.info("\n========================================");
			log.info("🎉 기존 설문 AI 처리 완료!");
			log.info("========================================");

		} catch (Exception e) {
			log.error("❌ AI 처리 중 오류 발생: {}", e.getMessage(), e);
		}
	}
}
