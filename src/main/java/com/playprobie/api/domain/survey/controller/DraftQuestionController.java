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

import com.playprobie.api.domain.survey.dto.DraftQuestionResponse;
import com.playprobie.api.domain.survey.dto.QuestionReviewResponse;
import com.playprobie.api.domain.survey.dto.UpdateDraftQuestionRequest;
import com.playprobie.api.domain.survey.service.DraftQuestionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DraftQuestionController {

    private final DraftQuestionService draftQuestionService;

    @PostMapping("/surveys/{surveyId}/generate-questions")
    public ResponseEntity<List<DraftQuestionResponse>> generateQuestions(@PathVariable Long surveyId) {
        List<DraftQuestionResponse> questions = draftQuestionService.generateQuestions(surveyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(questions);
    }

    @GetMapping("/surveys/{surveyId}/draft-questions")
    public ResponseEntity<List<DraftQuestionResponse>> getDraftQuestions(@PathVariable Long surveyId) {
        List<DraftQuestionResponse> questions = draftQuestionService.getDraftQuestions(surveyId);
        return ResponseEntity.ok(questions);
    }

    @PutMapping("/draft-questions/{draftQuestionId}")
    public ResponseEntity<DraftQuestionResponse> updateDraftQuestion(
            @PathVariable Long draftQuestionId,
            @Valid @RequestBody UpdateDraftQuestionRequest request) {
        DraftQuestionResponse response = draftQuestionService.updateDraftQuestion(draftQuestionId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 질문 리뷰 - 피드백 + 대안 3개 제공
     */
    @PostMapping("/draft-questions/{draftQuestionId}/review")
    public ResponseEntity<QuestionReviewResponse> reviewQuestion(@PathVariable Long draftQuestionId) {
        QuestionReviewResponse response = draftQuestionService.reviewQuestion(draftQuestionId);
        return ResponseEntity.ok(response);
    }
}
