package com.playprobie.api.domain.interview.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	@Enumerated(EnumType.STRING)
	@Column(name = "validity")
	private AnswerValidity validity;

	@Enumerated(EnumType.STRING)
	@Column(name = "quality")
	private AnswerQuality quality;

	@Builder
	public LogAnalysis(String sentiment, String topicCluster, AnswerValidity validity, AnswerQuality quality) {
		this.sentiment = sentiment;
		this.topicCluster = topicCluster;
		this.validity = validity;
		this.quality = quality;
	}
}
