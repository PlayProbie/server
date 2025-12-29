package com.playprobie.api.domain.survey.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.survey.domain.DraftQuestion;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.dto.DraftQuestionResponse;
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

    /**
     * AI를 통해 질문 10개 자동 생성
     */
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

    /**
     * 설문의 임시 질문 목록 조회
     */
    public List<DraftQuestionResponse> getDraftQuestions(Long surveyId) {
        return draftQuestionRepository.findBySurveyIdOrderByOrderAsc(surveyId)
                .stream()
                .map(DraftQuestionResponse::from)
                .toList();
    }

    /**
     * 임시 질문 수정
     */
    @Transactional
    public DraftQuestionResponse updateDraftQuestion(Long draftQuestionId, UpdateDraftQuestionRequest request) {
        DraftQuestion draftQuestion = draftQuestionRepository.findById(draftQuestionId)
                .orElseThrow(EntityNotFoundException::new);

        draftQuestion.updateContent(request.qContent());

        return DraftQuestionResponse.from(draftQuestion);
    }
}
