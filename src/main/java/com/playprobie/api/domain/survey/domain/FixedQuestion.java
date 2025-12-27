package com.playprobie.api.domain.survey.domain;

import java.util.Objects;

import com.playprobie.api.global.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fixed_question")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode(of = {"id"}, callSuper = false)
public class FixedQuestion extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "fixed_q_id")
	private Long id;

	@Column(name = "survey_id", nullable = false)
	private Long surveyId;

	@Column(name = "q_content", columnDefinition = "TEXT")
	private String content;

	@Column(name = "q_order")
	private Integer order;

	@Builder
	public FixedQuestion(Long surveyId, String content, Integer order) {
		this.surveyId = Objects.requireNonNull(surveyId, "FixedQuestion 생성 시 surveyId는 필수입니다");
		this.content = content;
		this.order = order;
	}
}