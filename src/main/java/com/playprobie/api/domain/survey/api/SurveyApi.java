package com.playprobie.api.domain.survey.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.survey.dto.request.AiQuestionsRequest;
import com.playprobie.api.domain.survey.dto.CreateFixedQuestionsRequest;
import com.playprobie.api.domain.survey.dto.request.CreateSurveyRequest;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.domain.survey.dto.FixedQuestionsCountResponse;
import com.playprobie.api.domain.survey.dto.QuestionFeedbackResponse;
import com.playprobie.api.domain.survey.dto.QuestionFeedbackRequest;
import com.playprobie.api.domain.survey.dto.response.SurveyResponse;
import com.playprobie.api.domain.survey.application.SurveyService;
import com.playprobie.api.global.common.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/surveys")
@RequiredArgsConstructor
public class SurveyApi {

    private final SurveyService surveyService;

    /**
     * 설문 생성
     * POST /surveys
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SurveyResponse>> createSurvey(@Valid @RequestBody CreateSurveyRequest request) {
        SurveyResponse response = surveyService.createSurvey(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    /**
     * 설문 조회
     * GET /surveys/{surveyId}
     */
    @GetMapping("/{surveyId}")
    public ResponseEntity<ApiResponse<SurveyResponse>> getSurvey(@PathVariable Long surveyId) {
        SurveyResponse response = surveyService.getSurvey(surveyId);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * AI 질문 자동 생성 (미리보기, DB 저장 X)
     * POST /surveys/ai-questions
     */
    @PostMapping("/ai-questions")
    public ResponseEntity<ApiResponse<List<String>>> generateAiQuestions(
            @Valid @RequestBody AiQuestionsRequest request) {
        List<String> result = surveyService.generateAiQuestions(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(result));
    }

    /**
     * 질문 피드백 (다중 질문에 대한 피드백 + 대안 제공)
     * POST /surveys/question-feedback
     */
    @PostMapping("/question-feedback")
    public ResponseEntity<ApiResponse<QuestionFeedbackResponse>> getQuestionFeedback(
            @Valid @RequestBody QuestionFeedbackRequest request) {
        String question = request.questions().get(0);
        String gameGenre = String.join(", ", request.gameGenre());
        QuestionFeedbackResponse feedback = surveyService.getQuestionFeedback(
                request.gameName(),
                gameGenre,
                request.gameContext(),
                request.testPurpose(),
                question);
        return ResponseEntity.ok(ApiResponse.of(feedback));
    }

    /**
     * 고정 질문 저장
     * POST /surveys/fixed_questions
     */
    @PostMapping("/fixed-questions")
    public ResponseEntity<ApiResponse<FixedQuestionsCountResponse>> createFixedQuestions(
            @Valid @RequestBody CreateFixedQuestionsRequest request) {
        FixedQuestionsCountResponse response = surveyService.createFixedQuestions(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    /**
     * 확정(CONFIRMED) 질문 목록 조회
     * GET /surveys/{surveyId}/questions
     */
    @GetMapping("/{surveyId}/questions")
    public ResponseEntity<ApiResponse<List<FixedQuestionResponse>>> getConfirmedQuestions(
            @PathVariable Long surveyId) {
        List<FixedQuestionResponse> questions = surveyService.getConfirmedQuestions(surveyId);
        return ResponseEntity.ok(ApiResponse.of(questions));
    }
}
