package com.playprobie.api.domain.survey.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.survey.domain.DraftQuestion;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.dto.DraftQuestionResponse;
import com.playprobie.api.domain.survey.dto.QuestionReviewResponse;
import com.playprobie.api.domain.survey.dto.UpdateDraftQuestionRequest;
import com.playprobie.api.domain.survey.repository.DraftQuestionRepository;
import com.playprobie.api.global.error.exception.EntityNotFoundException;
import com.playprobie.api.infra.ai.AiClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DraftQuestionService {

    private final DraftQuestionRepository draftQuestionRepository;
    private final SurveyService surveyService;
    private final AiClient aiClient;

    @Transactional
    public List<DraftQuestionResponse> generateQuestions(Long surveyId) {
        Survey survey = surveyService.getSurveyEntity(surveyId);

        String gameName = survey.getGame().getName();
        String gameContext = survey.getGame().getContext();
        String testPurpose = survey.getTestPurpose() != null ? survey.getTestPurpose().getCode() : "";

        List<String> generatedQuestions = aiClient.generateQuestions(gameName, gameContext, testPurpose, 10);

        List<DraftQuestion> draftQuestions = generatedQuestions.stream()
                .map((content) -> {
                    int order = generatedQuestions.indexOf(content) + 1;
                    return DraftQuestion.builder()
                            .surveyId(surveyId)
                            .content(content)
                            .order(order)
                            .build();
                })
                .toList();

        List<DraftQuestion> savedQuestions = draftQuestionRepository.saveAll(draftQuestions);

        return savedQuestions.stream()
                .map(DraftQuestionResponse::from)
                .toList();
    }

    public List<DraftQuestionResponse> getDraftQuestions(Long surveyId) {
        return draftQuestionRepository.findBySurveyIdOrderByOrderAsc(surveyId)
                .stream()
                .map(DraftQuestionResponse::from)
                .toList();
    }

    @Transactional
    public DraftQuestionResponse updateDraftQuestion(Long draftQuestionId, UpdateDraftQuestionRequest request) {
        DraftQuestion draftQuestion = draftQuestionRepository.findById(draftQuestionId)
                .orElseThrow(EntityNotFoundException::new);

        draftQuestion.updateContent(request.qContent());

        return DraftQuestionResponse.from(draftQuestion);
    }

    /**
     * 질문 리뷰 - 피드백 + 대안 3개 제공
     */
    public QuestionReviewResponse reviewQuestion(Long draftQuestionId) {
        DraftQuestion draftQuestion = draftQuestionRepository.findById(draftQuestionId)
                .orElseThrow(EntityNotFoundException::new);

        AiClient.QuestionReview review = aiClient.reviewQuestion(draftQuestion.getContent());

        return new QuestionReviewResponse(
                draftQuestion.getId(),
                draftQuestion.getContent(),
                review.feedback(),
                review.alternatives());
    }
}
