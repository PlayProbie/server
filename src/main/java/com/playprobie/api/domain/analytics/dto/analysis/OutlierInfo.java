package com.playprobie.api.domain.analytics.dto.analysis;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutlierInfo {
	private int count;
	private String summary;

	@JsonProperty("answer_ids")
	private List<String> answerIds;
}
