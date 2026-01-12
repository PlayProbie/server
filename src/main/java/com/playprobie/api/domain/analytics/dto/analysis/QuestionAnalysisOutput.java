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

	@Setter
	@JsonProperty("participant_stats")
	private ParticipantStats participantStats;

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ParticipantStats {
		@JsonProperty("age_groups")
		private Map<String, Integer> ageGroups;

		@JsonProperty("genders")
		private Map<String, Integer> genders;

		@JsonProperty("genres")
		private Map<String, Integer> genres;
	}
}
