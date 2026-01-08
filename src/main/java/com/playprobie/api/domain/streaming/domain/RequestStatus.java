package com.playprobie.api.domain.streaming.domain;

public enum RequestStatus {
	PENDING,
	PROCESSING,
	COMPLETED,
	FAILED,
	FAILED_FATAL // 수동 개입 필요
}
