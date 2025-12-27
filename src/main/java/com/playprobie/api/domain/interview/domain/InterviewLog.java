package com.playprobie.api.domain.interview.domain;

import java.util.Objects;

import com.playprobie.api.global.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "interview_log", uniqueConstraints = {
		@UniqueConstraint(name = "uk_session_turn", columnNames = { "session_id", "turn_num" })
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode(of = { "id" }, callSuper = false)
public class InterviewLog extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "log_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false)
	private SurveySession session;

	@Column(name = "fixed_q_id", nullable = false)
	private Long fixedQuestionId;

	@Column(name = "turn_num")
	private Integer turnNum;

	@Enumerated(EnumType.STRING)
	@Column(name = "q_type")
	private QuestionType type;

	@Column(name = "question_text", columnDefinition = "TEXT")
	private String questionText;

	@Column(name = "answer_text", columnDefinition = "TEXT")
	private String answerText;

	@Column(name = "tokens_used")
	private Integer tokensUsed;

	@Embedded // 분석 결과(감정, 토픽) 분리
	private LogAnalysis analysis;

	@Builder
	public InterviewLog(SurveySession session, Long fixedQuestionId, Integer turnNum,
			QuestionType type, String questionText, String answerText) {
		this.session = Objects.requireNonNull(session, "InterviewLog 생성 시 session은 필수입니다");
		this.fixedQuestionId = Objects.requireNonNull(fixedQuestionId, "InterviewLog 생성 시 fixedQuestionId는 필수입니다");
		this.turnNum = turnNum;
		this.type = type;
		this.questionText = questionText;
		this.answerText = answerText;
	}

	// 분석 결과 업데이트 (도메인 메서드)
	public void updateAnalysis(LogAnalysis analysis, Integer tokensUsed) {
		this.analysis = analysis;
		this.tokensUsed = tokensUsed;
	}
}