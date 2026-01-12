package com.playprobie.api.domain.analytics.dto.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GEQScores {
	private int competence;
	private int immersion;
	private int flow;
	private int tension;
	private int challenge;

	@JsonProperty("positive_affect")
	private int positiveAffect;

	@JsonProperty("negative_affect")
	private int negativeAffect;
}
