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
public class ClusterInfo {
	private String summary;
	private double percentage;
	private int count;

	@JsonProperty("emotion_type")
	private String emotionType;

	@JsonProperty("geq_scores")
	private GEQScores geqScores;

	@JsonProperty("emotion_detail")
	private String emotionDetail;

	@JsonProperty("answer_ids")
	private List<String> answerIds;

	private int satisfaction;

	private List<String> keywords;

	@JsonProperty("representative_answer_ids")
	private List<String> representativeAnswerIds;
}
