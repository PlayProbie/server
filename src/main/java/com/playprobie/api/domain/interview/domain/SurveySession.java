package com.playprobie.api.domain.interview.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.global.error.exception.EntityNotFoundException;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인터뷰 세션 Entity
 *
 * <p>
 * <b>[BaseTimeEntity 미상속 이유]</b>
 * <ul>
 * <li>startedAt/endedAt이 비즈니스적으로 더 중요</li>
 * <li>createdAt = startedAt으로 대체 가능</li>
 * <li>updatedAt은 세션 특성상 의미 없음 (상태 전이만 발생)</li>
 * </ul>
 */
@Entity
@Table(name = "survey_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode(of = {"id"}, callSuper = false)
public class SurveySession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "session_id")
	private Long id;

	@UuidGenerator(style = UuidGenerator.Style.TIME)
	@Column(name = "session_uuid")
	private UUID uuid;

	/**
	 * 연결된 설문 (필수)
	 *
	 * <p>
	 * ⚠️ LAZY 로딩: 대량 조회 시 fetch join 사용 권장
	 * </p>
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "survey_id", nullable = false)
	private Survey survey;

	/**
	 * 초기 생성 시점에 null일 수 있으며
	 * 추후 업데이트
	 */
	@Embedded
	private TesterProfile testerProfile;

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private SessionStatus status;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	/** AWS GameLift Streams Session ID */
	@Column(name = "aws_session_id")
	private String awsSessionId;

	/** 스트리밍 연결 시각 */
	@Column(name = "connected_at")
	private LocalDateTime connectedAt;

	/** 스트리밍 세션 종료 시각 */
	@Column(name = "terminated_at")
	private LocalDateTime terminatedAt;

	@Builder
	public SurveySession(Survey survey, TesterProfile testerProfile) {
		this.survey = Objects.requireNonNull(survey, "SurveySession 생성 시 Survey는 필수입니다");
		this.uuid = UUID.randomUUID();
		this.testerProfile = testerProfile;
		this.status = SessionStatus.IN_PROGRESS;
		this.startedAt = LocalDateTime.now();
	}

	/**
	 * @param testerProfile
	 * @desc 설문이 종료 후 tester정보를 업데이트
	 */
	public void registerTesterProfile(TesterProfile testerProfile) {
		this.testerProfile = testerProfile;
	}

	public void updateTesterProfile(TesterProfile testerProfile) {
		this.testerProfile = testerProfile;
	}

	/**
	 * 세션을 정상 완료 처리합니다.
	 * IN_PROGRESS 상태에서만 호출 가능합니다.
	 *
	 * @throws IllegalStateException 이미 종료된 세션인 경우
	 */
	public void complete() {
		if (this.status.isFinished()) {
			throw new IllegalStateException("이미 종료된 세션은 완료 처리할 수 없습니다. 현재 상태: " + this.status);
		}
		this.status = SessionStatus.COMPLETED;
		this.endedAt = LocalDateTime.now();
	}

	/**
	 * 세션을 이탈(중단) 처리합니다.
	 * IN_PROGRESS 상태에서만 호출 가능합니다.
	 *
	 * @throws IllegalStateException 이미 종료된 세션인 경우
	 */
	public void drop() {
		if (this.status.isFinished()) {
			throw new IllegalStateException("이미 종료된 세션은 이탈 처리할 수 없습니다. 현재 상태: " + this.status);
		}
		this.status = SessionStatus.DROPPED;
		this.endedAt = LocalDateTime.now();
	}

	/**
	 * 해당 세션이 파라미터로 넘어온 surveyId에 속하는지 검증
	 */
	public void validateSurveyId(Long surveyId) {
		if (this.survey == null || !this.survey.getId().equals(surveyId)) {
			throw new EntityNotFoundException();
		}
	}

	// ========== Streaming Session Methods ==========

	/**
	 * AWS GameLift 스트리밍 세션에 연결됨을 기록합니다.
	 *
	 * @param awsSessionId AWS GameLift Streams Session ID
	 */
	public void connect(String awsSessionId) {
		this.awsSessionId = awsSessionId;
		this.status = SessionStatus.CONNECTED;
		this.connectedAt = LocalDateTime.now();
	}

	/**
	 * 스트리밍 세션을 종료합니다.
	 */
	public void terminate() {
		if (this.status.isTerminated()) {
			return; // 이미 종료됨
		}
		this.status = SessionStatus.TERMINATED;
		this.terminatedAt = LocalDateTime.now();
	}
}
