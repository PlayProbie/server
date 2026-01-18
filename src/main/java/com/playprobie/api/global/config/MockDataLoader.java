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
				triggerAiProcessing();
			}
			return;
		}

		log.info("🚀 Mock 데이터 로딩 시작...");

		try (InputStream inputStream = getClass().getResourceAsStream("/data/mock_data.json")) {
			if (inputStream == null) {
				log.warn("⚠️ mock_data.json 파일을 찾을 수 없습니다.");
				return;
			}

			Map<String, Object> data = objectMapper.readValue(inputStream, new TypeReference<>() {});

			// 데이터 로딩은 별도 트랜잭션에서 실행
			loadDataWithTransaction(data);
		}

		log.info("✅ Mock 데이터 로딩 완료!");

		// AI 처리는 트랜잭션 외부에서 실행 (deadlock 방지)
		triggerAiProcessing();
	}

	@Transactional
	protected void loadDataWithTransaction(Map<String, Object> data) {
		loadData(data);
	}

	/**
	 * AI Embedding 및 Analytics 처리를 트리거합니다.
	 *
	 * <p>
	 * 처리 순서:
	 * </p>
	 * <ol>
	 * <li>AI 서버 연결 상태 확인 (최대 30회 재시도, 각 30초 대기)</li>
	 * <li>완료된 세션을 배치 단위로 나누어 처리 (배치 크기: 10개 세션)</li>
	 * <li>각 배치 내에서는 병렬 처리 (최대 50개 동시 실행)</li>
	 * <li>BERTopic 기반 Analytics 실행 및 DB 저장</li>
	 * </ol>
	 */
	private void triggerAiProcessing() {
		try {
			// 1. 완료된 세션 목록 조회
			List<SurveySession> completedSessions = surveySessionRepository.findAll()
				.stream()
				.filter(s -> s.getStatus() == com.playprobie.api.domain.interview.domain.SessionStatus.COMPLETED)
				.collect(Collectors.toList());

			if (completedSessions.isEmpty()) {
				log.info("⏩ 완료된 세션이 없습니다. AI 처리를 건너뜁니다.");
				return;
			}

			// 0. AI 서버 상태 확인 및 대기
			log.info("⏳ AI 서버 연결 확인 중...");
			int maxRetries = 30; // 최대 30회 시도 (15분)
			int retryCount = 0;
			boolean isAiServerReady = false;

			while (retryCount < maxRetries) {
				if (aiClient.checkHealth()) {
					isAiServerReady = true;
					break;
				}
				retryCount++;
				log.warn("⚠️ AI 서버에 연결할 수 없습니다. 30초 후 재시도합니다... ({}/{})", retryCount, maxRetries);
				Thread.sleep(30000);
			}

			if (!isAiServerReady) {
				log.error("❌ AI 서버가 준비되지 않아 AI 처리를 건너뜁니다.");
				return;
			}

			// Survey UUID를 미리 조회 (LazyInitializationException 방지)
			Long firstSurveyId = completedSessions.get(0).getSurvey().getId();
			Survey survey = surveyRepository.findById(firstSurveyId).orElseThrow();
			String surveyUuid = survey.getUuid().toString();

			log.info("🚀 AI Embedding 처리 시작 (총 {}개 세션, Survey UUID={})...", completedSessions.size(),
				surveyUuid);

			// 2. 배치 처리 설정
			final int BATCH_SIZE = 10; // 배치 크기: 10개 세션씩
			final int CONCURRENCY_LIMIT = 50; // 동시 처리 제한

			java.util.concurrent.atomic.AtomicInteger totalCompletedEmbeddings = new java.util.concurrent.atomic.AtomicInteger(
				0);
			java.util.concurrent.atomic.AtomicInteger totalFailedEmbeddings = new java.util.concurrent.atomic.AtomicInteger(
				0);

			// 3. 세션을 배치 단위로 나누어 처리
			int totalBatches = (int)Math.ceil((double)completedSessions.size() / BATCH_SIZE);
			log.info("📦 총 {}개 배치로 나누어 처리 (배치당 최대 {}개 세션)", totalBatches, BATCH_SIZE);

			for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
				final int currentBatchIndex = batchIndex; // 람다에서 사용하기 위해 final 변수로 복사
				int startIdx = currentBatchIndex * BATCH_SIZE;
				int endIdx = Math.min(startIdx + BATCH_SIZE, completedSessions.size());
				List<SurveySession> batchSessions = completedSessions.subList(startIdx, endIdx);

				log.info("🔄 배치 {}/{} 처리 중... (세션 {}-{})", currentBatchIndex + 1, totalBatches, startIdx + 1,
					endIdx);

				// 현재 배치에 대한 Embedding 태스크 생성
				List<reactor.core.publisher.Mono<Void>> batchTasks = new java.util.ArrayList<>();
				java.util.concurrent.atomic.AtomicInteger batchEmbeddingCount = new java.util.concurrent.atomic.AtomicInteger(
					0);

				for (SurveySession session : batchSessions) {
					String sessionId = session.getUuid().toString();

					// 세션의 InterviewLog를 고정질문별로 그룹핑
					Map<Long, List<InterviewLog>> logsByFixedQuestion = interviewLogRepository
						.findBySessionIdOrderByTurnNumAsc(session.getId())
						.stream()
						.collect(Collectors.groupingBy(InterviewLog::getFixedQuestionId));

					for (Map.Entry<Long, List<InterviewLog>> entry : logsByFixedQuestion.entrySet()) {
						Long fixedQuestionId = entry.getKey();
						List<InterviewLog> logs = entry.getValue();

						// Q&A 쌍 생성
						List<com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest.QaPair> qaPairs = logs
							.stream()
							.filter(l -> l.getAnswerText() != null)
							.map(l -> com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest.QaPair
								.of(
									l.getQuestionText(),
									l.getAnswerText(),
									l.getType().name()))
							.collect(Collectors.toList());

						if (!qaPairs.isEmpty()) {
							batchEmbeddingCount.incrementAndGet();

							// Metadata 생성
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

							// autoTriggerAnalysis = false로 설정하여 자동 트리거 방지
							com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest request = com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest
								.builder()
								.sessionId(sessionId)
								.surveyUuid(surveyUuid)
								.fixedQuestionId(fixedQuestionId)
								.qaPairs(qaPairs)
								.metadata(metadata)
								.autoTriggerAnalysis(false)
								.build();

							// Mono 태스크 생성
							reactor.core.publisher.Mono<Void> task = aiClient
								.embedSessionData(request)
								.doOnSuccess(result -> {
									totalCompletedEmbeddings.incrementAndGet();
									log.debug("✅ Embedding 완료: session={}, fixedQuestionId={}",
										sessionId, fixedQuestionId);
								})
								.doOnError(error -> {
									totalFailedEmbeddings.incrementAndGet();
									log.error("❌ Embedding 실패: session={}, fixedQuestionId={}, error={}",
										sessionId, fixedQuestionId,
										error.getMessage());
								})
								.onErrorResume(e -> reactor.core.publisher.Mono.empty())
								.then();

							batchTasks.add(task);
						}
					}
				}

				// 현재 배치의 Embedding 실행
				log.info("📤 배치 {}/{}: {}개 Embedding 요청 전송 (동시성 제한: {})", currentBatchIndex + 1, totalBatches,
					batchEmbeddingCount.get(), CONCURRENCY_LIMIT);

				reactor.core.publisher.Flux.fromIterable(batchTasks)
					.flatMap(mono -> mono.subscribeOn(
						reactor.core.scheduler.Schedulers.boundedElastic()), CONCURRENCY_LIMIT)
					.doOnComplete(() -> log.info("✅ 배치 {}/{} 완료 (성공: {}, 실패: {})",
						currentBatchIndex + 1, totalBatches,
						totalCompletedEmbeddings.get(), totalFailedEmbeddings.get()))
					.doOnError(e -> log.error("💥 Embedding 배치 {}/{} 에러: {}", currentBatchIndex + 1,
						totalBatches, e.getMessage()))
					.blockLast(java.time.Duration.ofMinutes(5)); // 배치당 최대 5분 대기

				log.info("🏁 배치 {}/{} 처리 완료", currentBatchIndex + 1, totalBatches);
			}

			log.info("✅ 모든 Embedding 완료: 총 성공 {}, 총 실패 {}", totalCompletedEmbeddings.get(),
				totalFailedEmbeddings.get());

			// 4. Analytics 배치 처리
			log.info("🚀 Analytics 시작 (surveyUuid={})...", surveyUuid);

			java.util.UUID surveyUuidObj = java.util.UUID.fromString(surveyUuid);

			// 질문 목록 조회
			List<FixedQuestion> questions = fixedQuestionRepository.findBySurveyIdOrderByOrderAsc(survey.getId());

			if (questions.isEmpty()) {
				log.warn("⚠️ 분석할 질문이 없습니다.");
			} else {
				java.util.concurrent.atomic.AtomicInteger totalCompletedAnalytics = new java.util.concurrent.atomic.AtomicInteger(
					0);
				java.util.concurrent.atomic.AtomicInteger totalFailedAnalytics = new java.util.concurrent.atomic.AtomicInteger(
					0);

				// 질문수가 적으므로 배치 처리 없이 순차 처리
				log.info("🔄 Analytics 처리 중... (총 {}개 질문)", questions.size());

				for (FixedQuestion question : questions) {
					try {
						log.debug("🔍 분석 시작: questionId={}", question.getId());
						analyticsService.analyzeSingleQuestion(surveyUuidObj, question.getId());
						totalCompletedAnalytics.incrementAndGet();
						log.debug("✅ Analytics 완료: questionId={}", question.getId());
					} catch (Exception error) {
						totalFailedAnalytics.incrementAndGet();
						log.error("❌ Analytics 실패: questionId={}, error={}", question.getId(), error.getMessage());
					}
				}

				log.info("✅ 모든 Analytics 완료: 총 성공 {}, 총 실패 {}", totalCompletedAnalytics.get(),
					totalFailedAnalytics.get());

				// 5. Survey Summary 생성
				log.info("🚀 Survey Summary 생성 시작...");
				try {
					// 분석 결과에서 meta_summary 추출
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
						log.info("📝 meta_summary {}개 추출 완료, AI 종합 평가 요청 중...", metaSummaries.size());
						String surveySummaryResult = aiClient.generateSurveySummary(metaSummaries)
							.block(java.time.Duration.ofMinutes(2));

						if (surveySummaryResult != null && !surveySummaryResult.isBlank()) {
							survey.updateSurveySummary(surveySummaryResult);
							surveyRepository.save(survey);
							log.info("✅ Survey Summary 저장 완료: {}", surveySummaryResult);
						}
					} else {
						log.warn("⚠️ meta_summary가 없어 Survey Summary를 건너뜁니다.");
					}
				} catch (Exception e) {
					log.error("❌ Survey Summary 생성 실패: {}", e.getMessage());
				}
			}

			log.info("✅ AI 처리 완료!");

		} catch (Exception e) {
			log.error("❌ AI 처리 중 오류 발생: {}", e.getMessage(), e);
		}
	}

	/**
	 * JSON 데이터를 DB에 저장합니다.
	 *
	 * <p>
	 * 처리 순서:
	 * </p>
	 * <ol>
	 * <li>Demo User 생성 (email: demo@playprobie.com, password: demo1234)</li>
	 * <li>Demo Workspace 생성 (고정 UUID: 00000000-0000-0000-0000-000000000000)</li>
	 * <li>Game 생성</li>
	 * <li>Survey 생성</li>
	 * <li>FixedQuestion 생성</li>
	 * <li>SurveySession 및 InterviewLog 생성</li>
	 * </ol>
	 *
	 * @param data mock_data.json에서 읽은 Map 데이터
	 */
	private void loadData(Map<String, Object> data) {
		log.info("\n========================================");
		log.info("🚀 Mock Data 로딩 시작");
		log.info("========================================\n");

		// 0. Demo User & Workspace 생성
		// 로그인: email=demo@playprobie.com, password=demo1234
		User demoUser = userRepository.save(User.builder()
			.email("demo@playprobie.com")
			.password(passwordEncoder.encode("demo1234"))
			.name("Demo User")
			.build());
		log.info("💾 [0/4] Demo User 저장 완료: ID={}, email={}", demoUser.getId(), demoUser.getEmail());

		Workspace workspace = workspaceRepository.save(Workspace.builder()
			.uuid(java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")) // Demo용 고정 UUID
			.name("Demo Workspace")
			.description("Mock 데이터용 데모 워크스페이스")
			.build());

		workspaceMemberRepository.save(WorkspaceMember.builder()
			.workspace(workspace)
			.user(demoUser)
			.role(WorkspaceRole.OWNER)
			.build());
		log.info("💾 [0/4] Workspace 저장 완료: ID={}, Name={}, UUID={}",
			workspace.getId(), workspace.getName(), workspace.getUuid());

		// 1. Game 생성 (JSON에서 로드)
		Map<String, Object> gameData = objectMapper.convertValue(data.get("game"),
			new TypeReference<Map<String, Object>>() {});

		// genres 배열 처리 (mock_data.json에서 ["RPG", "ACTION"] 형식)
		List<String> genreStrings = objectMapper.convertValue(gameData.get("genres"),
			new TypeReference<List<String>>() {});
		List<GameGenre> genres = genreStrings.stream()
			.map(GameGenre::valueOf)
			.collect(Collectors.toList());

		Game game = gameRepository.save(Game.builder()
			.workspace(workspace)
			.name((String)gameData.get("name"))
			.genres(genres)
			.context((String)gameData.get("description"))
			.build());
		log.info("💾 [1/4] Game 저장 완료: {}, UUID={}, genres={}", game.getName(), game.getUuid(), genres);

		// 2. Survey 생성
		Map<String, Object> surveyData = objectMapper.convertValue(data.get("survey"),
			new TypeReference<Map<String, Object>>() {});

		// testPurpose 매핑
		String testPurposeStr = (String)surveyData.get("testPurpose");
		TestPurpose testPurpose = TestPurpose.valueOf(testPurposeStr);

		// testStage 매핑 (optional)
		TestStage testStage = null;
		String testStageStr = (String)surveyData.get("testStage");
		if (testStageStr != null) {
			testStage = TestStage.valueOf(testStageStr);
		}

		// themePriorities 매핑 (required, 1-3개)
		List<String> themePriorities = objectMapper.convertValue(surveyData.get("themePriorities"),
			new TypeReference<List<String>>() {});

		// themeDetails 매핑 (optional)
		Map<String, List<String>> themeDetails = objectMapper.convertValue(surveyData.get("themeDetails"),
			new TypeReference<Map<String, List<String>>>() {});

		// versionNote 매핑 (optional)
		String versionNote = (String)surveyData.get("versionNote");

		Survey survey = surveyRepository.save(Survey.builder()
			.game(game)
			.name((String)surveyData.get("name"))
			.testPurpose(testPurpose)
			.testStage(testStage)
			.themePriorities(themePriorities)
			.themeDetails(themeDetails)
			.versionNote(versionNote)
			.startAt(LocalDateTime.now().minusDays(7))
			.endAt(LocalDateTime.now().plusDays(7))
			.build());

		log.info("💾 [2/4] Survey 저장 완료: ID={}, Name={}, testStage={}, themePriorities={}",
			survey.getId(), survey.getName(), testStage, themePriorities);

		// 3. FixedQuestion 생성
		List<Map<String, Object>> questionsData = objectMapper.convertValue(surveyData.get("questions"),
			new TypeReference<List<Map<String, Object>>>() {});
		for (Map<String, Object> qData : questionsData) {
			fixedQuestionRepository.save(FixedQuestion.builder()
				.surveyId(survey.getId())
				.content((String)qData.get("content"))
				.order((Integer)qData.get("order"))
				.status(QuestionStatus.CONFIRMED)
				.build());
		}
		log.info("💾 [3/4] FixedQuestion {}개 저장 완료 (Survey ID={})", questionsData.size(), survey.getId());

		// 4. Session & Logs 생성 (JSON 기반)
		List<Map<String, Object>> sessionsData = objectMapper.convertValue(data.get("sessions"),
			new TypeReference<List<Map<String, Object>>>() {});
		int logCount = 0;

		for (Map<String, Object> sData : sessionsData) {
			// TesterProfile 생성 (JSON에서 로드)
			Map<String, Object> profileData = objectMapper.convertValue(sData.get("profile"),
				new TypeReference<Map<String, Object>>() {});

			TesterProfile testerProfile = TesterProfile.builder()
				.testerId((String)profileData.get("testerId"))
				.ageGroup((String)profileData.get("ageGroup"))
				.gender((String)profileData.get("gender"))
				.preferGenre((String)profileData.get("preferGenre"))
				.build();

			// Session 생성 (이미 완료 상태로)
			SurveySession session = SurveySession.builder()
				.survey(survey)
				.testerProfile(testerProfile)
				.build();
			session.complete(); // 상태 완료 처리
			surveySessionRepository.save(session);

			// Logs 생성
			List<Map<String, Object>> logsData = objectMapper.convertValue(sData.get("logs"),
				new TypeReference<List<Map<String, Object>>>() {});
			for (Map<String, Object> lData : logsData) {
				Long fixedQuestionId = ((Number)lData.get("fixedQuestionId")).longValue();

				interviewLogRepository.save(InterviewLog.builder()
					.session(session)
					.fixedQuestionId(fixedQuestionId)
					.turnNum((Integer)lData.get("turnNum"))
					.type(QuestionType.valueOf((String)lData.get("type")))
					.questionText((String)lData.get("questionText"))
					.answerText((String)lData.get("answerText"))
					.build());
				logCount++;
			}
		}
		log.info("💾 [4/4] SurveySession {}개, InterviewLog {}개 저장 완료", sessionsData.size(), logCount);
	}
}
