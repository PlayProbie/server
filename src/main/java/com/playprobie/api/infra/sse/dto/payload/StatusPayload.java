package com.playprobie.api.infra.sse.dto.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

// 1. 단순 상태용 (start, done)
@Getter
@Builder
public class StatusPayload {
	private String status;
}