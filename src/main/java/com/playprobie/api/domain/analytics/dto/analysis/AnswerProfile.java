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
public class AnswerProfile {
	@JsonProperty("age_group")
	private String ageGroup;

	@JsonProperty("gender")
	private String gender;

	@JsonProperty("prefer_genre")
	private String preferGenre;
}
