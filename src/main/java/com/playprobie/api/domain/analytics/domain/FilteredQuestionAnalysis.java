package com.playprobie.api.domain.analytics.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 필터링된 질문 분석 결과 저장
 * 필터 조건별로 별도 분석 결과를 캐싱
 */
@Entity
@Table(name = "filtered_question_analysis", uniqueConstraints = {
	@jakarta.persistence.UniqueConstraint(name = "uk_filtered_analysis", columnNames = {"fixed_q_id",
		"filter_signature"})
}, indexes = {
	@Index(name = "idx_fixed_q_id_filter_sig", columnList = "fixed_q_id, filter_signature")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class FilteredQuestionAnalysis {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "fixed_q_id", nullable = false)
	private Long fixedQuestionId;

	@Column(name = "filter_signature", nullable = false, length = 500)
	private String filterSignature;

	@Column(name = "result_json", columnDefinition = "TEXT")
	private String resultJson;

	@Builder
	public FilteredQuestionAnalysis(Long fixedQuestionId, String filterSignature, String resultJson) {
		this.fixedQuestionId = Objects.requireNonNull(fixedQuestionId, "fixedQuestionId는 필수입니다");
		this.filterSignature = Objects.requireNonNull(filterSignature, "filterSignature는 필수입니다");
		this.resultJson = resultJson;
	}

	public void updateResultJson(String resultJson) {
		this.resultJson = resultJson;
	}
}
