package com.playprobie.api.domain.analytics.dto.analysis;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionAnalysisOutput {
	@JsonProperty("question_id")
	private Long questionId;

	@JsonProperty("total_answers")
	private int totalAnswers;

	private List<ClusterInfo> clusters;

	private SentimentInfo sentiment;

	private OutlierInfo outliers;

	@JsonProperty("meta_summary")
	private String metaSummary;

	@Setter
	@JsonProperty("answer_profiles")
	private Map<String, AnswerProfile> answerProfiles;
}
