package com.playprobie.api.domain.survey.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.survey.application.SurveyService;
import com.playprobie.api.domain.survey.dto.CreateFixedQuestionsRequest;
import com.playprobie.api.domain.survey.dto.FixedQuestionResponse;
import com.playprobie.api.domain.survey.dto.FixedQuestionsCountResponse;
import com.playprobie.api.domain.survey.dto.QuestionFeedbackRequest;
import com.playprobie.api.domain.survey.dto.QuestionFeedbackResponse;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.survey.dto.request.AiQuestionsRequest;
import com.playprobie.api.domain.survey.dto.request.CreateSurveyRequest;
import com.playprobie.api.domain.survey.dto.request.UpdateSurveyStatusRequest;
import com.playprobie.api.domain.survey.dto.response.SurveyResponse;
import com.playprobie.api.domain.survey.dto.response.UpdateSurveyStatusResponse;
import com.playprobie.api.global.common.response.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/surveys")
@RequiredArgsConstructor
@Tag(name = "Survey", description = "설문 관리 API")
public class SurveyController {

	private final SurveyService surveyService;

	/**
	 * 설문 목록 조회
	 */
	@GetMapping
	@Operation(summary = "설문 목록 조회", description = "게임별 또는 전체 설문 목록을 조회합니다.")
	public ResponseEntity<CommonResponse<List<SurveyResponse>>> getSurveys(
		@AuthenticationPrincipal(expression = "user")
		User user,
		@RequestParam(name = "game_uuid", required = false)
		UUID gameUuid) {
		List<SurveyResponse> response = surveyService.getSurveys(gameUuid, user);
		return ResponseEntity.ok(CommonResponse.of(response));
	}

	/**
	 * 설문 생성
	 */
	@PostMapping
	@Operation(summary = "설문 생성", description = "새로운 설문을 생성합니다.")
	public ResponseEntity<CommonResponse<SurveyResponse>> createSurvey(
		@AuthenticationPrincipal(expression = "user")
		User user,
		@Valid @RequestBody
		CreateSurveyRequest request) {
		SurveyResponse response = surveyService.createSurvey(request, user);
		return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.of(response));
	}

	/**
	 * 설문 조회
	 */
	@GetMapping("/{surveyUuid}")
	@Operation(summary = "설문 조회", description = "설문 상세 정보를 조회합니다.")
	public ResponseEntity<CommonResponse<SurveyResponse>> getSurvey(
		@AuthenticationPrincipal(expression = "user")
		User user,
		@PathVariable(name = "surveyUuid")
		UUID surveyUuid) {
		SurveyResponse response = surveyService.getSurveyByUuid(surveyUuid, user);
		return ResponseEntity.ok(CommonResponse.of(response));
	}

	/**
	 * AI 질문 자동 생성
	 */
	@PostMapping("/ai-questions")
	@Operation(summary = "AI 질문 생성", description = "AI를 통해 추천 질문 목록을 생성합니다.")
	public ResponseEntity<CommonResponse<List<String>>> generateAiQuestions(
		@Valid @RequestBody
		AiQuestionsRequest request) {
		List<String> result = surveyService.generateAiQuestions(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.of(result));
	}

	/**
	 * 질문 피드백
	 */
	@PostMapping("/question-feedback")
	@Operation(summary = "질문 피드백", description = "작성된 질문에 대한 AI 피드백 및 대안을 제공합니다.")
	public ResponseEntity<CommonResponse<QuestionFeedbackResponse>> getQuestionFeedback(
		@Valid @RequestBody
		QuestionFeedbackRequest request) {
		String question = request.questions().get(0);
		String gameGenre = String.join(", ", request.gameGenre());
		QuestionFeedbackResponse feedback = surveyService.getQuestionFeedback(
			request.gameName(),
			gameGenre,
			request.gameContext(),
			request.testPurpose(),
			question);
		return ResponseEntity.ok(CommonResponse.of(feedback));
	}

	/**
	 * 고정 질문 저장
	 */
	@PostMapping("/fixed-questions")
	@Operation(summary = "고정 질문 저장", description = "확정된 질문들을 설문에 저장합니다.")
	public ResponseEntity<CommonResponse<FixedQuestionsCountResponse>> createFixedQuestions(
		@AuthenticationPrincipal(expression = "user")
		User user,
		@Valid @RequestBody
		CreateFixedQuestionsRequest request) {
		FixedQuestionsCountResponse response = surveyService.createFixedQuestions(request, user);
		return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.of(response));
	}

	/**
	 * 확정 질문 목록 조회
	 */
	@GetMapping("/{surveyUuid}/questions")
	@Operation(summary = "확정 질문 목록 조회")
	public ResponseEntity<CommonResponse<List<FixedQuestionResponse>>> getConfirmedQuestions(
		@AuthenticationPrincipal(expression = "user")
		User user,
		@PathVariable(name = "surveyUuid")
		UUID surveyUuid) {
		List<FixedQuestionResponse> questions = surveyService.getConfirmedQuestions(surveyUuid, user);
		return ResponseEntity.ok(CommonResponse.of(questions));
	}

	/**
	 * 설문 상태 업데이트 (ACTIVE / CLOSED)
	 */
	@PatchMapping("/{surveyUuid}/status")
	@Operation(summary = "설문 상태 업데이트", description = "설문을 활성화(Scale-out)하거나 종료(Cleanup)합니다.")
	public ResponseEntity<CommonResponse<UpdateSurveyStatusResponse>> updateSurveyStatus(
		@AuthenticationPrincipal(expression = "user")
		User user,
		@PathVariable(name = "surveyUuid")
		UUID surveyUuid,
		@Valid @RequestBody
		UpdateSurveyStatusRequest request) {
		UpdateSurveyStatusResponse response = surveyService.updateSurveyStatus(surveyUuid, request, user);
		return ResponseEntity.ok(CommonResponse.of(response));
	}
}
