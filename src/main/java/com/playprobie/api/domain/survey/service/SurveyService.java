package com.playprobie.api.domain.survey.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.service.GameService;
import com.playprobie.api.domain.survey.domain.DraftQuestion;
import com.playprobie.api.domain.survey.domain.FixedQuestion;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.domain.TestPurpose;
import com.playprobie.api.domain.survey.dto.CreateSurveyRequest;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.domain.survey.dto.SurveyResponse;
import com.playprobie.api.domain.survey.repository.DraftQuestionRepository;
import com.playprobie.api.domain.survey.repository.FixedQuestionRepository;
import com.playprobie.api.domain.survey.repository.SurveyRepository;
import com.playprobie.api.global.error.exception.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyService {

    private final SurveyRepository surveyRepository;
    private final FixedQuestionRepository fixedQuestionRepository;
    private final DraftQuestionRepository draftQuestionRepository;
    private final GameService gameService;

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
        return SurveyResponse.from(savedSurvey);
    }

    public SurveyResponse getSurvey(Long surveyId) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(EntityNotFoundException::new);
        return SurveyResponse.from(survey);
    }

    public List<FixedQuestionResponse> getQuestions(Long surveyId) {
        if (!surveyRepository.existsById(surveyId)) {
            throw new EntityNotFoundException();
        }
        return fixedQuestionRepository.findBySurveyIdOrderByOrderAsc(surveyId)
                .stream()
                .map(FixedQuestionResponse::from)
                .toList();
    }

    /*
     * 설문 확정 - DraftQuestion → FixedQuestion 복사 후 Draft 삭제
     */
    @Transactional
    public List<FixedQuestionResponse> confirmSurvey(Long surveyId) {
        /* 해당 설문지 존재 확인 */
        if (!surveyRepository.existsById(surveyId)) {
            throw new EntityNotFoundException();
        }
        /* 임시 질문 조회 */
        List<DraftQuestion> draftQuestions = draftQuestionRepository.findBySurveyIdOrderByOrderAsc(surveyId);
        /* 임시 질문이 없으면 에러 */
        if (draftQuestions.isEmpty()) {
            throw new IllegalStateException("확정할 질문이 없습니다.");
        }
        /* 임시 질문 → 고정 질문 복사 */
        List<FixedQuestion> fixedQuestions = draftQuestions.stream()
                .map(draft -> FixedQuestion.builder()
                        .surveyId(draft.getSurveyId())
                        .content(draft.getContent())
                        .order(draft.getOrder())
                        .build())
                .toList();

        List<FixedQuestion> savedQuestions = fixedQuestionRepository.saveAll(fixedQuestions);
        /* 임시 질문 삭제 */
        draftQuestionRepository.deleteBySurveyId(surveyId);
        /* 고정 질문 반환 */
        return savedQuestions.stream()
                .map(FixedQuestionResponse::from)
                .toList();
    }

    public Survey getSurveyEntity(Long surveyId) {
        return surveyRepository.findById(surveyId)
                .orElseThrow(EntityNotFoundException::new);
    }

    private TestPurpose parseTestPurpose(String code) {
        for (TestPurpose tp : TestPurpose.values()) {
            if (tp.getCode().equals(code)) {
                return tp;
            }
        }
        throw new IllegalArgumentException("Invalid test purpose code: " + code);
    }
}
