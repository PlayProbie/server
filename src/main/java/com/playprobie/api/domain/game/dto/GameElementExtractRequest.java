package com.playprobie.api.domain.game.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GameElementExtractRequest(
	String gameName,
	List<String> genres,
	String gameDescription) {
}
