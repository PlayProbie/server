package com.playprobie.api.infra.ai.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Builder;

/**
 * 추천된 개별 질문 DTO
 */
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecommendedQuestion(
	String id,
	String text,
	String originalText,
	String template,
	String slotKey,
	String purposeCategory,
	String purposeSubcategory,
	Double similarityScore,
	Double goalMatchScore,
	Double adoptionRate,
	Double finalScore,
	Object embedding) {
}
