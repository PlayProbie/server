package com.playprobie.api.domain.survey.dto.response;

import com.playprobie.api.domain.survey.domain.ThemeDetail;

public record ThemeDetailResponse(String code, String displayName, String category) {
	public static ThemeDetailResponse from(ThemeDetail detail) {
		return new ThemeDetailResponse(
			detail.getCode(),
			detail.getDisplayName(),
			detail.getCategory().getCode());
	}
}
