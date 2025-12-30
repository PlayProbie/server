package com.playprobie.api.infra.sse.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SseResponse {

	private String type;
	private String text;
	private String action;

	public static SseResponse of(String type, String text, String action) {
		return SseResponse.builder()
			.type(type)
			.text(text)
			.action(action)
			.build();
	}
}
