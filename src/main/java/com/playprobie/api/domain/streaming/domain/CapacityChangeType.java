package com.playprobie.api.domain.streaming.domain;

public enum CapacityChangeType {
	START_TEST, // 0 -> 1
	STOP_TEST, // 1 -> 0
	ACTIVATE, // 0 -> N
	DEACTIVATE // N -> 0
}
