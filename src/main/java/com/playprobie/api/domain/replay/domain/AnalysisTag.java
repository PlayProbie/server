package com.playprobie.api.domain.replay.domain;

import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.global.domain.BaseTimeEntity;

import jakarta.persistence.Column;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 분석 태그 Entity
 * 입력 패턴 분석 결과를 저장
 */
@Entity
@Table(name = "analysis_tag")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class AnalysisTag extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "tag_id")
	private Long id;

	@UuidGenerator(style = UuidGenerator.Style.TIME)
	@Column(name = "tag_uuid", nullable = false, unique = true)
	private UUID uuid;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false)
	private SurveySession session;

	@Enumerated(EnumType.STRING)
	@Column(name = "insight_type", nullable = false, length = 20)
	private InsightType insightType;

	@Column(name = "video_time_ms", nullable = false)
	private Long videoTimeMs;

	@Column(name = "duration_ms")
	private Integer durationMs;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata", columnDefinition = "json")
	private String metadata;

	@Column(name = "is_asked", nullable = false)
	private Boolean isAsked = false;

	@Column(name = "answer_text", columnDefinition = "TEXT")
	private String answerText;

	@Builder
	public AnalysisTag(SurveySession session, InsightType insightType,
		Long videoTimeMs, Integer durationMs, String metadata) {
		this.session = Objects.requireNonNull(session, "AnalysisTag 생성 시 session은 필수입니다");
		this.insightType = Objects.requireNonNull(insightType, "AnalysisTag 생성 시 insightType은 필수입니다");
		this.videoTimeMs = Objects.requireNonNull(videoTimeMs, "AnalysisTag 생성 시 videoTimeMs는 필수입니다");
		this.durationMs = durationMs;
		this.metadata = metadata;
		this.isAsked = false;
	}

	/**
	 * 질문 완료 및 답변 저장
	 */
	public void markAsAsked(String answerText) {
		this.isAsked = true;
		this.answerText = answerText;
	}
}
