package com.playprobie.api.domain.survey.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.service.GameService;
import com.playprobie.api.domain.survey.domain.FixedQuestion;
import com.playprobie.api.domain.survey.domain.QuestionStatus;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.domain.TestPurpose;
import com.playprobie.api.domain.survey.dto.CreateSurveyRequest;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.domain.survey.dto.QuestionReviewResponse;
import com.playprobie.api.domain.survey.dto.SurveyResponse;
import com.playprobie.api.domain.survey.dto.UpdateQuestionRequest;
import com.playprobie.api.domain.survey.repository.FixedQuestionRepository;
import com.playprobie.api.domain.survey.repository.SurveyRepository;
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
        savedSurvey.setSurveyUrl(surveyUrl);

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

    // ========== Question 생성/수정/리뷰 ==========

    /*
     * AI를 통해 질문 10개 자동 생성 (DRAFT 상태로 저장)
     */
    @Transactional
    public List<FixedQuestionResponse> generateQuestions(Long surveyId) {
        Survey survey = getSurveyEntity(surveyId);

        String gameName = survey.getGame().getName();
        String gameGenre = survey.getGame().getGenres().stream()
                .map(g -> g.getDisplayName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String gameContext = survey.getGame().getContext();
        String testPurpose = survey.getTestPurpose() != null ? survey.getTestPurpose().getCode() : "";

        List<String> generatedQuestions = aiClient.generateQuestions(gameName, gameGenre, gameContext, testPurpose, 10);

        List<FixedQuestion> questions = generatedQuestions.stream()
                .map((content) -> {
                    int order = generatedQuestions.indexOf(content) + 1;
                    return FixedQuestion.builder()
                            .surveyId(surveyId)
                            .content(content)
                            .order(order)
                            .status(QuestionStatus.DRAFT)
                            .build();
                })
                .toList();

        List<FixedQuestion> savedQuestions = fixedQuestionRepository.saveAll(questions);

        return savedQuestions.stream()
                .map(FixedQuestionResponse::from)
                .toList();
    }

    /*
     * 임시(DRAFT) 질문 목록 조회
     */
    public List<FixedQuestionResponse> getDraftQuestions(Long surveyId) {
        return fixedQuestionRepository.findBySurveyIdAndStatusOrderByOrderAsc(surveyId, QuestionStatus.DRAFT)
                .stream()
                .map(FixedQuestionResponse::from)
                .toList();
    }

    /*
     * 확정(CONFIRMED) 질문 목록 조회
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

    /*
     * 질문 수정 (DRAFT 상태인 경우만)
     */
    @Transactional
    public FixedQuestionResponse updateQuestion(Long questionId, UpdateQuestionRequest request) {
        FixedQuestion question = fixedQuestionRepository.findById(questionId)
                .orElseThrow(EntityNotFoundException::new);

        if (!question.isDraft()) {
            throw new IllegalStateException("확정된 질문은 수정할 수 없습니다.");
        }

        question.updateContent(request.qContent());

        return FixedQuestionResponse.from(question);
    }

    /*
     * 질문 리뷰 - 피드백 + 대안 3개 제공
     */
    public QuestionReviewResponse reviewQuestion(Long questionId) {
        FixedQuestion question = fixedQuestionRepository.findById(questionId)
                .orElseThrow(EntityNotFoundException::new);

        AiClient.QuestionReview review = aiClient.reviewQuestion(question.getContent());

        return new QuestionReviewResponse(
                question.getId(),
                question.getContent(),
                review.feedback(),
                review.alternatives());
    }

    // ========== 설문 확정 ==========

    /*
     * 설문 확정 - DRAFT 질문들을 CONFIRMED로 변경
     */
    @Transactional
    public List<FixedQuestionResponse> confirmSurvey(Long surveyId) {
        if (!surveyRepository.existsById(surveyId)) {
            throw new EntityNotFoundException();
        }

        List<FixedQuestion> draftQuestions = fixedQuestionRepository
                .findBySurveyIdAndStatusOrderByOrderAsc(surveyId, QuestionStatus.DRAFT);

        if (draftQuestions.isEmpty()) {
            throw new IllegalStateException("확정할 질문이 없습니다.");
        }

        draftQuestions.forEach(FixedQuestion::confirm);

        return draftQuestions.stream()
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
