package com.playprobie.api.infra.sse.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SseResponse<T> {

	private String id;
	private String event;
	private T data;

	// 팩토리 메서드: 타입 추론을 통해 깔끔하게 생성
	public static <T> SseResponse<T> of(String id, String event, T data) {
		return SseResponse.<T>builder()
			.id(id)
			.event(event)
			.data(data)
			.build();
	}
}
