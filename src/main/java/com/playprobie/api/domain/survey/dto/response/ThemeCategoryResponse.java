package com.playprobie.api.domain.survey.dto.response;

import com.playprobie.api.domain.survey.domain.ThemeCategory;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "테마 카테고리 응답 DTO")
public record ThemeCategoryResponse(

	@Schema(description = "카테고리 코드", example = "UX")
	String code,

	@Schema(description = "표시 이름", example = "사용자 경험")
	String displayName) {

	public static ThemeCategoryResponse from(ThemeCategory category) {
		return new ThemeCategoryResponse(category.getCode(), category.getDisplayName());
	}
}
