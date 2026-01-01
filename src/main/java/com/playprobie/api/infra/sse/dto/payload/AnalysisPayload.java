package com.playprobie.api.infra.sse.dto.payload;

import lombok.Builder;
import lombok.Getter;

// 2. 분석용 (analyze_answer)
@Getter
@Builder
public class AnalysisPayload {
	private String action;
	private String analysis;
}