package com.playprobie.api.infra.data;

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
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameGenre;
import com.playprobie.api.domain.interview.dao.InterviewLogRepository;
import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.domain.QuestionType;
import com.playprobie.api.domain.interview.domain.SessionStatus;
import com.playprobie.api.domain.interview.domain.SurveySession;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class MockDataLoader implements CommandLineRunner {

        private final GameRepository gameRepository;
        private final SurveyRepository surveyRepository;
        private final FixedQuestionRepository fixedQuestionRepository;
        private final SurveySessionRepository surveySessionRepository;
        private final InterviewLogRepository interviewLogRepository;
        private final ObjectMapper objectMapper;
        private final com.playprobie.api.infra.ai.AiClient aiClient;
        private final com.playprobie.api.domain.analytics.dao.QuestionResponseAnalysisRepository analysisRepository;
        private final UserRepository userRepository;
        private final WorkspaceRepository workspaceRepository;
        private final WorkspaceMemberRepository workspaceMemberRepository;
        private final PasswordEncoder passwordEncoder;
        private final com.playprobie.api.domain.analytics.application.AnalyticsService analyticsService;

        @Override
        public void run(String... args) throws Exception {
                if (surveyRepository.count() > 0) {
                        log.info("⏩ 데이터가 이미 존재합니다. Mock 데이터 로딩을 건너뜠니다.");

                        // Analytics도 이미 존재하는지 확인
                        if (analysisRepository.count() > 0) {
                                log.info("⏩ Analytics 데이터도 이미 존재합니다. AI 처리를 건너뜠니다.");
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

                        Map<String, Object> data = objectMapper.readValue(inputStream, new TypeReference<>() {
                        });

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

        private void triggerAiProcessing() {
                try {
                        // 1. 완료된 세션 목록 조회
                        List<SurveySession> completedSessions = surveySessionRepository.findAll()
                                        .stream()
                                        .filter(s -> s.getStatus() == SessionStatus.COMPLETED)
                                        .collect(Collectors.toList());

                        if (completedSessions.isEmpty()) {
                                log.info("⏩ 완료된 세션이 없습니다. AI 처리를 건너뜠니다.");
                                return;
                        }

                        // Survey UUID를 미리 조회 (LazyInitializationException 방지)
                        Long firstSurveyId = completedSessions.get(0).getSurvey().getId();
                        Survey survey = surveyRepository.findById(firstSurveyId).orElseThrow();
                        String surveyUuid = survey.getUuid().toString();

                        log.info("🚀 AI Embedding 처리 시작 (총 {}개 세션, Survey UUID={})...", completedSessions.size(),
                                        surveyUuid);

                        // 2. 세션별로 Embedding 요청 생성 (Flux 사용 - Non-blocking)
                        java.util.concurrent.atomic.AtomicInteger completedEmbeddings = new java.util.concurrent.atomic.AtomicInteger(
                                        0);
                        java.util.concurrent.atomic.AtomicInteger failedEmbeddings = new java.util.concurrent.atomic.AtomicInteger(
                                        0);
                        java.util.concurrent.atomic.AtomicInteger totalEmbeddings = new java.util.concurrent.atomic.AtomicInteger(
                                        0);

                        // 세션별 Embedding Mono 목록 생성
                        List<reactor.core.publisher.Mono<Void>> embeddingTasks = new java.util.ArrayList<>();

                        for (SurveySession session : completedSessions) {
                                String sessionId = session.getUuid().toString(); // UUID 사용 (InterviewApi와 동일)

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
                                                totalEmbeddings.incrementAndGet();

                                                // autoTriggerAnalysis = false로 설정하여 자동 트리거 방지
                                                com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest request = com.playprobie.api.infra.ai.dto.request.SessionEmbeddingRequest
                                                                .builder()
                                                                .sessionId(sessionId)
                                                                .surveyUuid(surveyUuid)
                                                                .fixedQuestionId(fixedQuestionId)
                                                                .qaPairs(qaPairs)
                                                                .autoTriggerAnalysis(false) // 자동 트리거 방지!
                                                                .build();

                                                // Mono 태스크 생성
                                                reactor.core.publisher.Mono<Void> task = aiClient
                                                                .embedSessionData(request)
                                                                .doOnSuccess(result -> {
                                                                        completedEmbeddings.incrementAndGet();
                                                                        log.debug("✅ Embedding 완료: session={}, fixedQId={}",
                                                                                        sessionId, fixedQuestionId);
                                                                })
                                                                .doOnError(error -> {
                                                                        failedEmbeddings.incrementAndGet();
                                                                        log.error("❌ Embedding 실패: session={}, fixedQId={}, error={}",
                                                                                        sessionId, fixedQuestionId,
                                                                                        error.getMessage());
                                                                })
                                                                .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                                                                .then();

                                                embeddingTasks.add(task);
                                        }
                                }
                        }

                        log.info("📤 총 {}개 Embedding 요청 전송 (병렬 처리, 동시성 제한: 3)", totalEmbeddings.get());

                        // 3. flatMap으로 동시성 제한하여 실행 (최대 3개 동시 실행)
                        // subscribeOn(Schedulers.boundedElastic())으로 블로킹 안전하게 처리
                        reactor.core.publisher.Flux.fromIterable(embeddingTasks)
                                        .flatMap(mono -> mono.subscribeOn(
                                                        reactor.core.scheduler.Schedulers.boundedElastic()), 3) // 동시성
                                                                                                                // 제한:
                                                                                                                // 10 →
                                                                                                                // 3 (AI
                                                                                                                // 서버
                                                                                                                // 과부하
                                                                                                                // 방지)
                                        .doOnSubscribe(s -> log.info("🔄 Embedding Flux 구독 시작..."))
                                        .doOnComplete(() -> log.info("🏁 Embedding Flux 완료"))
                                        .doOnError(e -> log.error("💥 Embedding Flux 에러: {}", e.getMessage()))
                                        .blockLast(java.time.Duration.ofMinutes(5)); // 최대 5분 대기

                        log.info("✅ 모든 Embedding 완료: 성공 {}, 실패 {}", completedEmbeddings.get(), failedEmbeddings.get());

                        // 4. Analytics 트리거 및 DB 저장 (AnalyticsService 사용)
                        log.info("🚀 Analytics 시작 (surveyUuid={})...", surveyUuid);

                        // AnalyticsService.getSurveyAnalysis()를 사용하여 분석 실행 및 DB 저장
                        // 이 메서드는 내부적으로 analyzeAndSave()를 호출하여 결과를 QuestionResponseAnalysis 테이블에 저장
                        java.util.UUID surveyUuidObj = java.util.UUID.fromString(surveyUuid);
                        analyticsService.getSurveyAnalysis(surveyUuidObj)
                                        .doOnNext(result -> log.info("✅ Analytics 저장 완료: questionId={}",
                                                        result.fixedQuestionId()))
                                        .doOnError(e -> log.error("❌ Analytics 실패: {}", e.getMessage()))
                                        .blockLast(java.time.Duration.ofMinutes(10)); // 최대 10분 대기

                        log.info("✅ AI 처리 완료!");

                } catch (Exception e) {
                        log.error("❌ AI 처리 중 오류 발생: {}", e.getMessage(), e);
                }
        }

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
                                .uuid(java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")) // Demo용 고정
                                                                                                         // UUID
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
                Map<String, Object> gameData = (Map<String, Object>) data.get("game");

                // genres 배열 처리 (mock_data.json에서 ["RPG", "ACTION"] 형식)
                List<String> genreStrings = (List<String>) gameData.get("genres");
                List<GameGenre> genres = genreStrings.stream()
                                .map(GameGenre::valueOf)
                                .collect(Collectors.toList());

                Game game = gameRepository.save(Game.builder()
                                .workspace(workspace)
                                .name((String) gameData.get("name"))
                                .genres(genres)
                                .context((String) gameData.get("description"))
                                .build());
                log.info("💾 [1/4] Game 저장 완료: {}, UUID={}, genres={}", game.getName(), game.getUuid(), genres);

                // 2. Survey 생성
                Map<String, Object> surveyData = (Map<String, Object>) data.get("survey");

                // testPurpose 매핑
                String testPurposeStr = (String) surveyData.get("testPurpose");
                TestPurpose testPurpose = TestPurpose.valueOf(testPurposeStr);

                // testStage 매핑 (optional)
                TestStage testStage = null;
                String testStageStr = (String) surveyData.get("testStage");
                if (testStageStr != null) {
                        testStage = TestStage.valueOf(testStageStr);
                }

                // themePriorities 매핑 (required, 1-3개)
                List<String> themePriorities = (List<String>) surveyData.get("themePriorities");

                // themeDetails 매핑 (optional)
                Map<String, List<String>> themeDetails = (Map<String, List<String>>) surveyData.get("themeDetails");

                // versionNote 매핑 (optional)
                String versionNote = (String) surveyData.get("versionNote");

                Survey survey = surveyRepository.save(Survey.builder()
                                .game(game)
                                .name((String) surveyData.get("name"))
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
                List<Map<String, Object>> questionsData = (List<Map<String, Object>>) surveyData.get("questions");
                for (Map<String, Object> qData : questionsData) {
                        fixedQuestionRepository.save(FixedQuestion.builder()
                                        .surveyId(survey.getId())
                                        .content((String) qData.get("content"))
                                        .order((Integer) qData.get("order"))
                                        .status(QuestionStatus.CONFIRMED)
                                        .build());
                }
                log.info("💾 [3/4] FixedQuestion {}개 저장 완료 (Survey ID={})", questionsData.size(), survey.getId());

                // 4. Session & Logs 생성
                List<Map<String, Object>> sessionsData = (List<Map<String, Object>>) data.get("sessions");
                int logCount = 0;

                for (Map<String, Object> sData : sessionsData) {
                        // Session 생성 (이미 완료 상태로)
                        SurveySession session = SurveySession.builder()
                                        .survey(survey)
                                        .testerProfile(null) // Mock 데이터엔 프로필 없음
                                        .build();
                        session.complete(); // 상태 완료 처리
                        surveySessionRepository.save(session);

                        // Logs 생성
                        List<Map<String, Object>> logsData = (List<Map<String, Object>>) sData.get("logs");
                        for (Map<String, Object> lData : logsData) {
                                Long fixedQId = ((Number) lData.get("fixedQuestionId")).longValue();

                                interviewLogRepository.save(InterviewLog.builder()
                                                .session(session)
                                                .fixedQuestionId(fixedQId)
                                                .turnNum((Integer) lData.get("turnNum"))
                                                .type(QuestionType.valueOf((String) lData.get("type")))
                                                .questionText((String) lData.get("questionText"))
                                                .answerText((String) lData.get("answerText"))
                                                .build());
                                logCount++;
                        }
                }
                log.info("💾 [4/4] SurveySession {}개, InterviewLog {}개 저장 완료", sessionsData.size(), logCount);
        }
}
