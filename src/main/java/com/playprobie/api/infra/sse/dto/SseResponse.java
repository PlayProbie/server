package com.playprobie.api.infra.sse.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "SSE 응답 DTO")
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SseResponse<T> {

	@Schema(description = "이벤트 ID", example = "1")
	private String id;

	@Schema(description = "이벤트 이름", example = "interview-message")
	private String event;

	@Schema(description = "이벤트 데이터")
	private T data;

	public static <T> SseResponse<T> of(String id, String event, T data) {
		return SseResponse.<T>builder()
			.id(id)
			.event(event)
			.data(data)
			.build();
	}
}
