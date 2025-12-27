package com.playprobie.api.domain.interview.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LogAnalysis {

	@Column(name = "sentiment")
	private String sentiment;

	@Column(name = "topic_cluster")
	private String topicCluster;

	@Builder
	public LogAnalysis(String sentiment, String topicCluster) {
		this.sentiment = sentiment;
		this.topicCluster = topicCluster;
	}
}