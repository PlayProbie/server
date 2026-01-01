package com.playprobie.api.infra.sse.dto.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

// 3. 토큰 스트리밍용 (token)
@Getter
@Builder
public class TokenPayload {
	private String content;
}
