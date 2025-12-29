package com.playprobie.api.domain.survey.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.survey.dto.DraftQuestionResponse;
import com.playprobie.api.domain.survey.service.DraftQuestionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DraftQuestionController {

    private final DraftQuestionService draftQuestionService;

    /**
     * AI를 통해 질문 10개 자동 생성
     */
    @PostMapping("/surveys/{surveyId}/generate-questions")
    public ResponseEntity<List<DraftQuestionResponse>> generateQuestions(@PathVariable Long surveyId) {
        List<DraftQuestionResponse> questions = draftQuestionService.generateQuestions(surveyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(questions);
    }

    /**
     * 설문의 임시 질문 목록 조회
     */
    @GetMapping("/surveys/{surveyId}/draft-questions")
    public ResponseEntity<List<DraftQuestionResponse>> getDraftQuestions(@PathVariable Long surveyId) {
        List<DraftQuestionResponse> questions = draftQuestionService.getDraftQuestions(surveyId);
        return ResponseEntity.ok(questions);
    }
}
