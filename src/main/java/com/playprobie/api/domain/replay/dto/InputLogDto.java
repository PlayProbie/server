package com.playprobie.api.domain.replay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 개별 입력 로그 DTO (메모리 분석용)
 * 원시 로그는 DB에 저장하지 않고 분석 후 폐기
 */
public record InputLogDto(
	@JsonProperty("type")
	String type,

	@JsonProperty("media_time")
	Long mediaTime,

	@JsonProperty("client_ts")
	Long clientTs,

	// 키보드 이벤트 필드 (KEY_DOWN, KEY_UP)
	@JsonProperty("code")
	String code,

	@JsonProperty("key")
	String key,

	// 마우스 클릭 이벤트 필드 (MOUSE_DOWN, MOUSE_UP)
	@JsonProperty("button")
	Integer button,

	@JsonProperty("x")
	Integer x,

	@JsonProperty("y")
	Integer y,

	// 휠 이벤트 필드 (WHEEL)
	@JsonProperty("deltaX")
	Integer deltaX,

	@JsonProperty("deltaY")
	Integer deltaY,

	// 샘플링 여부 (MOUSE_MOVE, WHEEL)
	@JsonProperty("sampled")
	Boolean sampled) {
	/**
	 * 키보드 입력 이벤트인지 확인
	 */
	public boolean isKeyEvent() {
		return "KEY_DOWN".equals(type) || "KEY_UP".equals(type);
	}

	/**
	 * 마우스 클릭 이벤트인지 확인
	 */
	public boolean isMouseClickEvent() {
		return "MOUSE_DOWN".equals(type) || "MOUSE_UP".equals(type);
	}

	/**
	 * 입력 이벤트인지 확인 (분석 대상)
	 */
	public boolean isInputEvent() {
		return isKeyEvent() || isMouseClickEvent();
	}
}
