package com.playprobie.api.domain.survey.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.survey.dto.CreateSurveyRequest;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.domain.survey.dto.SurveyResponse;
import com.playprobie.api.domain.survey.service.SurveyService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

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

    /**
     * 설문에 속한 고정 질문 목록 조회
     * - 질문 순서(q_order)대로 정렬되어 반환
     */
    @GetMapping("/{surveyId}/questions")
    public ResponseEntity<List<FixedQuestionResponse>> getQuestions(@PathVariable Long surveyId) {
        List<FixedQuestionResponse> questions = surveyService.getQuestions(surveyId);
        return ResponseEntity.ok(questions);
    }

    /**
     * 설문 확정 - DraftQuestion → FixedQuestion 복사 후 Draft 삭제
     */
    @PostMapping("/{surveyId}/confirm")
    public ResponseEntity<List<FixedQuestionResponse>> confirmSurvey(@PathVariable Long surveyId) {
        List<FixedQuestionResponse> questions = surveyService.confirmSurvey(surveyId);
        return ResponseEntity.ok(questions);
    }
}
