package com.playprobie.api.domain.survey.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.survey.dto.CreateSurveyRequest;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.domain.survey.dto.QuestionReviewResponse;
import com.playprobie.api.domain.survey.dto.SurveyResponse;
import com.playprobie.api.domain.survey.dto.UpdateQuestionRequest;
import com.playprobie.api.domain.survey.service.SurveyService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    // ========== Survey CRUD ==========

    @PostMapping
    public ResponseEntity<SurveyResponse> createSurvey(@Valid @RequestBody CreateSurveyRequest request) {
        SurveyResponse response = surveyService.createSurvey(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{surveyId}")
    public ResponseEntity<SurveyResponse> getSurvey(@PathVariable Long surveyId) {
        SurveyResponse response = surveyService.getSurvey(surveyId);
        return ResponseEntity.ok(response);
    }

    // ========== 질문 생성/수정/리뷰 ==========

    /**
     * AI 질문 10개 자동 생성 (DRAFT 상태)
     */
    @PostMapping("/{surveyId}/generate-questions")
    public ResponseEntity<List<FixedQuestionResponse>> generateQuestions(@PathVariable Long surveyId) {
        List<FixedQuestionResponse> questions = surveyService.generateQuestions(surveyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(questions);
    }

    /**
     * 임시(DRAFT) 질문 목록 조회
     */
    @GetMapping("/{surveyId}/draft-questions")
    public ResponseEntity<List<FixedQuestionResponse>> getDraftQuestions(@PathVariable Long surveyId) {
        List<FixedQuestionResponse> questions = surveyService.getDraftQuestions(surveyId);
        return ResponseEntity.ok(questions);
    }

    /**
     * 확정(CONFIRMED) 질문 목록 조회
     */
    @GetMapping("/{surveyId}/questions")
    public ResponseEntity<List<FixedQuestionResponse>> getConfirmedQuestions(@PathVariable Long surveyId) {
        List<FixedQuestionResponse> questions = surveyService.getConfirmedQuestions(surveyId);
        return ResponseEntity.ok(questions);
    }

    /**
     * 질문 수정 (DRAFT 상태인 경우만)
     */
    @PutMapping("/{surveyId}/questions/{questionId}")
    public ResponseEntity<FixedQuestionResponse> updateQuestion(
            @PathVariable Long surveyId,
            @PathVariable Long questionId,
            @Valid @RequestBody UpdateQuestionRequest request) {
        FixedQuestionResponse response = surveyService.updateQuestion(questionId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 질문 리뷰 - 피드백 + 대안 3개 제공
     */
    @PostMapping("/{surveyId}/questions/{questionId}/review")
    public ResponseEntity<QuestionReviewResponse> reviewQuestion(
            @PathVariable Long surveyId,
            @PathVariable Long questionId) {
        QuestionReviewResponse response = surveyService.reviewQuestion(questionId);
        return ResponseEntity.ok(response);
    }

    // ========== 설문 확정 ==========

    /**
     * 설문 확정 - DRAFT 질문들을 CONFIRMED로 변경
     */
    @PostMapping("/{surveyId}/confirm")
    public ResponseEntity<List<FixedQuestionResponse>> confirmSurvey(@PathVariable Long surveyId) {
        List<FixedQuestionResponse> questions = surveyService.confirmSurvey(surveyId);
        return ResponseEntity.ok(questions);
    }
}
