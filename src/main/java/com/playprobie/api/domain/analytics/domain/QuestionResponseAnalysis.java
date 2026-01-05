package com.playprobie.api.domain.analytics.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설문 질문별 AI 분석 결과 저장
 * Primary Key: fixed_q_id
 */
@Entity
@Table(name = "question_response_analysis")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class QuestionResponseAnalysis {

	@Id
	@Column(name = "fixed_q_id")
	private Long fixedQuestionId;

	@Column(name = "survey_id", nullable = false)
	private Long surveyId;

	@Column(name = "result_json", columnDefinition = "TEXT", nullable = false)
	private String resultJson;

	@Column(name = "processed_answer_count", nullable = false)
	private Integer processedAnswerCount;

	public QuestionResponseAnalysis(Long fixedQuestionId, Long surveyId, String resultJson,
			Integer processedAnswerCount) {
		this.fixedQuestionId = Objects.requireNonNull(fixedQuestionId, "fixedQuestionId는 필수입니다");
		this.surveyId = Objects.requireNonNull(surveyId, "surveyId는 필수입니다");
		this.resultJson = Objects.requireNonNull(resultJson, "resultJson은 필수입니다");
		this.processedAnswerCount = Objects.requireNonNull(processedAnswerCount, "processedAnswerCount는 필수입니다");
	}

	public void updateResult(String resultJson, Integer processedAnswerCount) {
		this.resultJson = resultJson;
		this.processedAnswerCount = processedAnswerCount;
	}
}
