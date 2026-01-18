package com.playprobie.api.infra.ai.dto.response;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Builder;

/**
 * FastAPI /api/questions/recommend 응답 DTO
 */
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record QuestionRecommendResponse(
	List<RecommendedQuestion> questions,
	Integer totalCandidates,
	Map<String, Double> scoringWeightsUsed) {
}
