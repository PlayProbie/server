package com.playprobie.api.domain.survey.application;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.playprobie.api.domain.survey.domain.TestStage;
import com.playprobie.api.domain.survey.domain.ThemeCategory;
import com.playprobie.api.domain.survey.domain.ThemeDetail;
import com.playprobie.api.domain.survey.dto.response.ThemeCategoryResponse;
import com.playprobie.api.domain.survey.dto.response.ThemeDetailResponse;

/**
 * 테스트 단계별 테마 추천 서비스
 */
@Service
public class ThemeRecommendationService {

	/**
	 * 테스트 단계에 따른 추천 테마 목록 반환
	 * 우선순위에 따라 정렬된 ThemeCategoryResponse 목록 반환
	 */
	public List<ThemeCategoryResponse> getRecommendedThemes(String stageCode) {
		TestStage stage = parseTestStage(stageCode);
		List<ThemeCategory> themes = getRecommendedThemesByStage(stage);
		return themes.stream()
			.map(ThemeCategoryResponse::from)
			.toList();
	}

	/**
	 * 특정 테마 카테고리의 세부 항목 목록 반환
	 */
	public List<ThemeDetailResponse> getThemeDetails(String categoryCode) {
		ThemeCategory category = parseThemeCategory(categoryCode);
		List<ThemeDetail> details = getThemeDetailsByCategory(category);
		return details.stream()
			.map(ThemeDetailResponse::from)
			.toList();
	}

	/**
	 * 모든 테마 카테고리와 세부 항목을 맵으로 반환
	 */
	public Map<String, List<ThemeDetailResponse>> getAllThemes() {
		Map<ThemeCategory, List<ThemeDetail>> allThemes = getAllThemesWithDetails();
		return allThemes.entrySet().stream()
			.collect(Collectors.toMap(
				entry -> entry.getKey().getCode(),
				entry -> entry.getValue().stream()
					.map(ThemeDetailResponse::from)
					.toList()));
	}

	// ========== Private ==========

	private List<ThemeCategory> getRecommendedThemesByStage(TestStage stage) {
		return switch (stage) {
			case PROTOTYPE -> List.of(
				ThemeCategory.GAMEPLAY, // 게임성 검증 최우선
				ThemeCategory.UI_UX,
				ThemeCategory.BALANCE);
			case PLAYTEST -> List.of(
				ThemeCategory.UI_UX, // UI/UX 피드백 최우선
				ThemeCategory.GAMEPLAY,
				ThemeCategory.BALANCE,
				ThemeCategory.STORY,
				ThemeCategory.BUG);
			case PRE_LAUNCH -> List.of(
				ThemeCategory.BUG, // 버그 리포트 최우선
				ThemeCategory.OVERALL,
				ThemeCategory.BALANCE,
				ThemeCategory.UI_UX);
		};
	}

	private List<ThemeDetail> getThemeDetailsByCategory(ThemeCategory category) {
		return Arrays.stream(ThemeDetail.values())
			.filter(detail -> detail.getCategory() == category)
			.toList();
	}

	private Map<ThemeCategory, List<ThemeDetail>> getAllThemesWithDetails() {
		return Arrays.stream(ThemeDetail.values())
			.collect(Collectors.groupingBy(ThemeDetail::getCategory));
	}

	private TestStage parseTestStage(String code) {
		for (TestStage ts : TestStage.values()) {
			if (ts.getCode().equals(code) || ts.name().equals(code)) {
				return ts;
			}
		}
		throw new IllegalArgumentException("Invalid test stage: " + code);
	}

	private ThemeCategory parseThemeCategory(String code) {
		for (ThemeCategory tc : ThemeCategory.values()) {
			if (tc.getCode().equals(code) || tc.name().equals(code)) {
				return tc;
			}
		}
		throw new IllegalArgumentException("Invalid theme category: " + code);
	}
}
