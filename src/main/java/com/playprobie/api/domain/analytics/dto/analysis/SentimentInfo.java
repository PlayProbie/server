package com.playprobie.api.domain.analytics.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentInfo {
	private int score;
	private String label;
	private Distribution distribution;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Distribution {
		private double positive;
		private double neutral;
		private double negative;
	}
}
