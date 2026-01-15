package com.playprobie.api.domain.survey.dto.response;

import com.playprobie.api.domain.survey.domain.ThemeDetail;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "테마 세부 항목 응답 DTO")
public record ThemeDetailResponse(

	@Schema(description = "세부 항목 코드", example = "TUTORIAL")
	String code,

	@Schema(description = "표시 이름", example = "튜토리얼 경험")
	String displayName,

	@Schema(description = "소속 카테고리 코드", example = "UX")
	String category) {

	public static ThemeDetailResponse from(ThemeDetail detail) {
		return new ThemeDetailResponse(
			detail.getCode(),
			detail.getDisplayName(),
			detail.getCategory().getCode());
	}
}
