package com.playprobie.api.domain.survey.dto.response;

import com.playprobie.api.domain.survey.domain.ThemeCategory;

public record ThemeCategoryResponse(String code, String displayName) {
	public static ThemeCategoryResponse from(ThemeCategory category) {
		return new ThemeCategoryResponse(category.getCode(), category.getDisplayName());
	}
}
