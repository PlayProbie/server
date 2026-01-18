package com.playprobie.api.infra.ai.dto.request;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Builder;

/**
 * FastAPI /api/questions/recommend 요청 DTO
 */
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record QuestionRecommendRequest(
	String gameName,
	String gameDescription,
	List<String> genres,
	String testPhase,
	List<String> purposeCategories,
	List<String> purposeSubcategories,
	Map<String, String> extractedElements,
	Object adoptionStats,
	Integer topK,
	Object scoringWeights,
	Boolean shuffle) {
}
