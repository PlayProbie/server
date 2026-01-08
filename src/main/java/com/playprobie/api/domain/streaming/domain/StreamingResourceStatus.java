package com.playprobie.api.domain.streaming.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Streaming Resource 상태 Enum.
 *
 * <p>
 * JIT Provisioning 워크플로우에서의 리소스 상태를 나타냅니다.
 */
@Getter
@RequiredArgsConstructor
public enum StreamingResourceStatus {

	CREATING("생성 시작"),
	PENDING("연결 대기"),
	PROVISIONING("리소스 생성 중"),
	READY("준비 완료 (Capacity=0)"),
	SCALING_UP("용량 증가 중"),
	SCALING_DOWN("용량 감소 중"),
	TESTING("관리자 테스트 (Capacity=1)"),
	SCALING("확장 중"),
	ACTIVE("서비스 중 (Capacity=N)"),
	CLEANING("정리 중"),
	TERMINATED("삭제됨"),
	ERROR("오류 발생"),
	FAILED_FATAL("치명적 오류 (수동 복구 필요)");

	private final String description;

	/**
	 * 리소스가 사용 가능한 상태인지 확인합니다.
	 */
	public boolean isAvailable() {
		return this == READY || this == TESTING || this == ACTIVE;
	}

	/**
	 * 리소스가 삭제되었거나 정리 중인 상태인지 확인합니다.
	 */
	public boolean isTerminatedOrCleaning() {
		return this == TERMINATED || this == CLEANING;
	}

	/**
	 * 스케일링이 가능한 상태인지 확인합니다.
	 */
	public boolean canScale() {
		return this == READY || this == TESTING || this == ACTIVE;
	}
}
