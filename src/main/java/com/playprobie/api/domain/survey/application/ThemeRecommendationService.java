package com.playprobie.api.domain.survey.application;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.playprobie.api.domain.survey.domain.TestStage;
import com.playprobie.api.domain.survey.domain.ThemeCategory;
import com.playprobie.api.domain.survey.domain.ThemeDetail;

/**
 * 테스트 단계별 테마 추천 서비스
 */
@Service
public class ThemeRecommendationService {

	/**
	 * 테스트 단계에 따른 추천 테마 목록 반환
	 * 우선순위에 따라 정렬된 ThemeCategory 목록 반환
	 */
	public List<ThemeCategory> getRecommendedThemes(TestStage stage) {
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

	/**
	 * 특정 테마 카테고리의 세부 항목 목록 반환
	 */
	public List<ThemeDetail> getThemeDetails(ThemeCategory category) {
		return Arrays.stream(ThemeDetail.values())
			.filter(detail -> detail.getCategory() == category)
			.toList();
	}

	/**
	 * 모든 테마 카테고리와 세부 항목을 맵으로 반환
	 */
	public Map<ThemeCategory, List<ThemeDetail>> getAllThemesWithDetails() {
		return Arrays.stream(ThemeDetail.values())
			.collect(Collectors.groupingBy(ThemeDetail::getCategory));
	}
}
