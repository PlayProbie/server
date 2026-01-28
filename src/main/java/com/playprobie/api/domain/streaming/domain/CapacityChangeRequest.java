package com.playprobie.api.domain.streaming.domain;

import java.time.LocalDateTime;

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
 * 용량 변경 요청 추적 엔티티.
 *
 * <p>
 * 비동기 작업의 상태를 추적하기 위해 사용됩니다.
 */
@Entity
@Table(name = "capacity_change_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CapacityChangeRequest extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "resource_id", nullable = false)
	private StreamingResource resource;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CapacityChangeType type;

	@Column(name = "target_capacity", nullable = false)
	private Integer targetCapacity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RequestStatus status;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "retry_count", nullable = false)
	private Integer retryCount = 0;

	private static final int MAX_RETRY_COUNT = 3;

	@Builder
	public CapacityChangeRequest(StreamingResource resource, CapacityChangeType type,
		Integer targetCapacity) {
		this.resource = resource;
		this.type = type;
		this.targetCapacity = targetCapacity;
		this.status = RequestStatus.PENDING;
		this.retryCount = 0;
	}

	public static CapacityChangeRequest create(StreamingResource resource,
		CapacityChangeType type, int targetCapacity) {
		return CapacityChangeRequest.builder()
			.resource(resource)
			.type(type)
			.targetCapacity(targetCapacity)
			.build();
	}

	public void markProcessing() {
		this.status = RequestStatus.PROCESSING;
	}

	public void markCompleted() {
		this.status = RequestStatus.COMPLETED;
		this.completedAt = LocalDateTime.now();
	}

	public void markFailed(String errorMessage) {
		this.status = RequestStatus.FAILED;
		this.errorMessage = errorMessage;
		this.completedAt = LocalDateTime.now();
	}

	public void markFailedFatal(String errorMessage) {
		this.status = RequestStatus.FAILED_FATAL;
		this.errorMessage = errorMessage;
		this.completedAt = LocalDateTime.now();
	}

	/**
	 * 재시도 횟수를 증가시킵니다.
	 */
	public void incrementRetryCount() {
		this.retryCount++;
	}

	/**
	 * 최대 재시도 횟수를 초과했는지 확인합니다.
	 *
	 * @return 초과 시 true
	 */
	public boolean hasExceededMaxRetries() {
		return this.retryCount >= MAX_RETRY_COUNT;
	}

	/**
	 * 최대 재시도 횟수를 반환합니다.
	 */
	public static int getMaxRetryCount() {
		return MAX_RETRY_COUNT;
	}
}
