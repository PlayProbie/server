package com.playprobie.api.domain.survey.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.application.GameService;
import com.playprobie.api.domain.survey.domain.FixedQuestion;
import com.playprobie.api.domain.survey.domain.QuestionStatus;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.domain.TestPurpose;
import com.playprobie.api.domain.survey.dto.AiQuestionsRequest;
import com.playprobie.api.domain.survey.dto.CreateFixedQuestionsRequest;
import com.playprobie.api.domain.survey.dto.CreateSurveyRequest;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.domain.survey.dto.FixedQuestionsCountResponse;
import com.playprobie.api.domain.survey.dto.QuestionFeedbackItem;
import com.playprobie.api.domain.survey.dto.QuestionFeedbackRequest;
import com.playprobie.api.domain.survey.dto.SurveyResponse;
import com.playprobie.api.domain.survey.dao.FixedQuestionRepository;
import com.playprobie.api.domain.survey.dao.SurveyRepository;
import com.playprobie.api.global.error.exception.EntityNotFoundException;
import com.playprobie.api.global.util.HashIdEncoder;
import com.playprobie.api.infra.ai.AiClient;

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

    @Transactional
    public SurveyResponse createSurvey(CreateSurveyRequest request) {
        Game game = gameService.getGameEntity(request.gameId());
        TestPurpose testPurpose = parseTestPurpose(request.testPurpose());

        Survey survey = Survey.builder()
                .game(game)
                .name(request.surveyName())
                .testPurpose(testPurpose)
                .startAt(request.startedAt())
                .endAt(request.endedAt())
                .build();

        Survey savedSurvey = surveyRepository.save(survey);

        // URL 생성 (Base62 인코딩)
        String surveyUrl = "https://playprobie.shop/" + HashIdEncoder.encode(savedSurvey.getId());
        savedSurvey.assignUrl(surveyUrl);

        return SurveyResponse.from(savedSurvey);
    }

    public SurveyResponse getSurvey(Long surveyId) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(EntityNotFoundException::new);
        return SurveyResponse.from(survey);
    }

    public Survey getSurveyEntity(Long surveyId) {
        return surveyRepository.findById(surveyId)
                .orElseThrow(EntityNotFoundException::new);
    }

    // ========== AI 질문 생성 (미리보기) ==========

    /**
     * AI를 통해 질문 생성 (DB 저장 없이 미리보기)
     * POST /surveys/ai-questions
     */
    public List<String> generateAiQuestions(AiQuestionsRequest request) {
        String gameGenre = String.join(", ", request.gameGenre());

        return aiClient.generateQuestions(
                request.gameName(),
                gameGenre,
                request.gameContext(),
                request.testPurpose());
    }

    // ========== 질문 피드백 ==========

    /**
     * 질문에 대한 피드백 제공
     * POST /surveys/question-feedback
     * 
     * Note: FastAPI는 단일 질문에 대해 피드백을 받으므로, 각 질문에 대해 순차 호출
     */
    public List<QuestionFeedbackItem> getQuestionFeedback(QuestionFeedbackRequest request) {
        String gameGenre = String.join(", ", request.gameGenre());

        return request.questions().stream()
                .map(question -> {
                    // FastAPI의 /fixed-questions/feedback은 original_question + feedback을 받음
                    // 여기서는 question을 original_question으로, 기본 피드백 요청으로 처리
                    List<String> candidates = aiClient.getQuestionFeedback(
                            request.gameName(),
                            gameGenre,
                            request.gameContext(),
                            request.testPurpose(),
                            question,
                            "이 질문을 더 개선해주세요");

                    return new QuestionFeedbackItem(
                            question,
                            "질문을 분석하여 대안을 생성했습니다.",
                            candidates);
                })
                .toList();
    }

    // ========== 고정 질문 저장 ==========

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
}
