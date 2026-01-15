package com.playprobie.api.infra.sse.dto.payload;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorPayload {
	String message;
}
