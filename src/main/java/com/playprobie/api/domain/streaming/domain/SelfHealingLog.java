package com.playprobie.api.domain.streaming.domain;

import java.time.LocalDateTime;

import com.playprobie.api.global.domain.BaseTimeEntity;

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
 * Self-Healing 쿨다운 추적 Entity.
 *
 * <p>
 * ConcurrentHashMap 대신 DB 기반으로 쿨다운을 관리하여 Scale-Out 환경을 지원합니다.
 */
@Entity
@Table(name = "self_healing_log", indexes = {
	@Index(name = "idx_self_healing_resource_attempt", columnList = "resource_id, last_attempt_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SelfHealingLog extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "resource_id", nullable = false, unique = true)
	private Long resourceId;

	@Column(name = "last_attempt_at", nullable = false)
	private LocalDateTime lastAttemptAt;

	@Column(name = "attempt_count", nullable = false)
	private Integer attemptCount = 0;

	@Builder
	public SelfHealingLog(Long resourceId) {
		this.resourceId = resourceId;
		this.lastAttemptAt = LocalDateTime.now();
		this.attemptCount = 1;
	}

	/**
	 * Self-Healing 시도를 기록합니다.
	 */
	public void recordAttempt() {
		this.lastAttemptAt = LocalDateTime.now();
		this.attemptCount++;
	}

	/**
	 * 쿨다운이 활성 상태인지 확인합니다.
	 *
	 * @param cooldownSeconds 쿨다운 시간 (초)
	 * @return 쿨다운 중이면 true
	 */
	public boolean isCooldownActive(int cooldownSeconds) {
		return lastAttemptAt.plusSeconds(cooldownSeconds).isAfter(LocalDateTime.now());
	}
}
