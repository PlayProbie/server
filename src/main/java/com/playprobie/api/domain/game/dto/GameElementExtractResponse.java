package com.playprobie.api.domain.game.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GameElementExtractResponse(
	Map<String, String> elements,
	List<String> requiredFields,
	List<String> optionalFields,
	List<String> missingRequired) {
}
