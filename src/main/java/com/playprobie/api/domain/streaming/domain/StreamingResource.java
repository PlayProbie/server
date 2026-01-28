package com.playprobie.api.domain.streaming.domain;

import java.util.Objects;

import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.survey.domain.Survey;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JIT Streaming Resource Entity.
 *
 * <p>
 * 설문에 연결된 AWS GameLift Streams 리소스를 관리합니다.
 * Survey와 1:1 관계, GameBuild와 N:1 관계를 가집니다.
 */
@Entity
@Table(name = "streaming_resource")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamingResource extends BaseTimeEntity {

	@Column(name = "resource_uuid", nullable = false, unique = true)
	private java.util.UUID uuid = java.util.UUID.randomUUID();

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "resource_id")
	private Long id;

	@Column(name = "aws_application_id")
	private String awsApplicationId;

	@Column(name = "aws_stream_group_id")
	private String awsStreamGroupId;

	@Column(name = "instance_type", nullable = false, length = 50)
	private String instanceType;

	@Column(name = "os_type", nullable = false, length = 20)
	private String osType;

	@Column(name = "current_capacity")
	private Integer currentCapacity = 0;

	@Column(name = "max_capacity", nullable = false)
	private Integer maxCapacity;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private StreamingResourceStatus status = StreamingResourceStatus.CREATING;

	@Version
	@Column(name = "version")
	private Long version;

	// 롤백을 위한 이전 상태 저장 (DB 영속화로 서버 재시작 시에도 유지)
	@Enumerated(EnumType.STRING)
	@Column(name = "previous_status", length = 30)
	private StreamingResourceStatus previousStatus;

	@Column(name = "previous_capacity")
	private Integer previousCapacity;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "survey_id", nullable = false, unique = true)
	private Survey survey;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "build_id", nullable = false)
	private GameBuild build;

	@Builder
	public StreamingResource(Survey survey, GameBuild build, String instanceType, Integer maxCapacity) {
		this.survey = Objects.requireNonNull(survey, "StreamingResource 생성 시 Survey는 필수입니다");
		this.build = Objects.requireNonNull(build, "StreamingResource 생성 시 GameBuild는 필수입니다");
		this.instanceType = Objects.requireNonNull(instanceType, "StreamingResource 생성 시 instanceType은 필수입니다");
		this.maxCapacity = Objects.requireNonNull(maxCapacity, "StreamingResource 생성 시 maxCapacity는 필수입니다");
		this.osType = build.getOsType();
		this.status = StreamingResourceStatus.CREATING;
		this.currentCapacity = 0;
	}

	// ========== 상태 전이 메서드 ==========

	/**
	 * AWS Application 생성 완료 시 호출합니다.
	 */
	public void assignApplication(String awsApplicationId) {
		this.awsApplicationId = awsApplicationId;
		this.status = StreamingResourceStatus.PROVISIONING;
	}

	/**
	 * AWS StreamGroup 생성 및 연결 완료 시 호출합니다.
	 */
	public void assignStreamGroup(String awsStreamGroupId) {
		this.awsStreamGroupId = awsStreamGroupId;
		this.status = StreamingResourceStatus.READY;
	}

	// ========== [주의] 아래 레거시 메서드들은 Phase 1 리팩토링에서 삭제되었습니다 ==========
	// startTest(), stopTest(), activate() 메서드는 더 이상 사용되지 않습니다.
	// 대신 markScalingUp/markScalingDown/confirmStartTest/confirmStopTest 패턴을 사용하세요.
	// Two-Phase Transaction Pattern으로 DB 업데이트와 AWS API 호출을 분리합니다.
	// =================================================================================

	/**
	 * 스케일링 완료 시 호출합니다.
	 */
	public void markActive() {
		this.status = StreamingResourceStatus.ACTIVE;
	}

	// [New Methods for Two-Phase Transaction]

	public void markScalingUp(int targetCapacity) {
		// 화이트리스트 방식: 스케일링 가능한 상태(READY, TESTING, ACTIVE)에서만 허용
		if (!this.status.canScale()) {
			throw new IllegalStateException("스케일링을 시작할 수 없는 상태입니다: " + this.status);
		}
		// 롤백을 위해 이전 상태 저장
		this.previousStatus = this.status;
		this.previousCapacity = this.currentCapacity;

		this.status = StreamingResourceStatus.SCALING_UP;
		this.currentCapacity = targetCapacity;
	}

	public void markScalingDown() {
		// 롤백을 위해 이전 상태 저장
		this.previousStatus = this.status;
		this.previousCapacity = this.currentCapacity;

		this.status = StreamingResourceStatus.SCALING_DOWN;
		this.currentCapacity = 0;
	}

	public void confirmStartTest() {
		this.status = StreamingResourceStatus.TESTING;
		this.currentCapacity = 1;
	}

	public void confirmStopTest() {
		this.status = StreamingResourceStatus.READY;
		this.currentCapacity = 0;
	}

	public void rollbackScaling() {
		// 이전 상태로 복원 (ACTIVE 상태에서 스케일업 실패 시 서비스 중단 방지)
		if (this.previousStatus != null) {
			this.status = this.previousStatus;
			this.currentCapacity = this.previousCapacity != null ? this.previousCapacity : 0;
		} else {
			// 이전 상태가 없으면 기본값으로 READY 복원 (서버 재시작 등)
			this.status = StreamingResourceStatus.READY;
			this.currentCapacity = 0;
		}
		// 이전 상태 초기화
		this.previousStatus = null;
		this.previousCapacity = null;
	}

	/**
	 * 리소스 정리 시작 시 호출합니다.
	 */
	public void startCleanup() {
		this.status = StreamingResourceStatus.CLEANING;
	}

	/**
	 * 리소스 삭제 완료 시 호출합니다.
	 */
	public void terminate() {
		this.status = StreamingResourceStatus.TERMINATED;
		this.currentCapacity = 0;
	}

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	public void markError(String errorMessage) {
		this.status = StreamingResourceStatus.ERROR;
		this.errorMessage = errorMessage;
	}

	/**
	 * Capacity를 업데이트합니다.
	 */
	public void updateCapacity(int capacity) {
		this.currentCapacity = capacity;
	}
}
