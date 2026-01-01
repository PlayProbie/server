package com.playprobie.api.infra.ai.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GenerateFeedbackRequest {
	private String gameName;
	private String gameGenre;
	private String gameContext;
	private String testPurpose;
	private String originalQuestion;
}
