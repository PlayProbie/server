package com.playprobie.api.domain.survey.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.playprobie.api.domain.survey.application.ThemeRecommendationService;
import com.playprobie.api.domain.survey.domain.TestStage;
import com.playprobie.api.domain.survey.domain.ThemeCategory;
import com.playprobie.api.domain.survey.domain.ThemeDetail;
import com.playprobie.api.domain.survey.dto.response.ThemeCategoryResponse;
import com.playprobie.api.domain.survey.dto.response.ThemeDetailResponse;
import com.playprobie.api.global.common.response.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/themes")
@RequiredArgsConstructor
@Tag(name = "Theme API", description = "테스트 테마 및 추천 질문 카테고리 API")
public class ThemeController {

	private final ThemeRecommendationService themeRecommendationService;

	@GetMapping("/recommendations")
	@Operation(summary = "테스트 단계별 추천 테마 조회", description = "테스트 단계(PROTOTYPE, BETA 등)에 맞는 추천 테마 카테고리 목록을 반환합니다.")
	public ResponseEntity<CommonResponse<List<ThemeCategoryResponse>>> getRecommendedThemes(
		@Parameter(description = "테스트 단계 코드 (PROTOTYPE, CLOSED_BETA, OPEN_BETA, SOFT_LAUNCH, LIVE)", example = "PROTOTYPE") @RequestParam
		String stage) {
		TestStage testStage = parseTestStage(stage);
		List<ThemeCategory> themes = themeRecommendationService.getRecommendedThemes(testStage);

		List<ThemeCategoryResponse> response = themes.stream()
			.map(ThemeCategoryResponse::from)
			.toList();

		return ResponseEntity.ok(CommonResponse.of(response));
	}

	@GetMapping("/{category}/details")
	@Operation(summary = "테마 카테고리별 세부 항목 조회", description = "특정 테마 카테고리에 속하는 세부 질문 항목들을 반환합니다.")
	public ResponseEntity<CommonResponse<List<ThemeDetailResponse>>> getThemeDetails(
		@Parameter(description = "테마 카테고리 코드 (UX, GRAPHICS, BALANCE 등)", example = "UX") @PathVariable
		String category) {
		ThemeCategory themeCategory = parseThemeCategory(category);
		List<ThemeDetail> details = themeRecommendationService.getThemeDetails(themeCategory);

		List<ThemeDetailResponse> response = details.stream()
			.map(ThemeDetailResponse::from)
			.toList();

		return ResponseEntity.ok(CommonResponse.of(response));
	}

	@GetMapping("/all")
	@Operation(summary = "전체 테마 카테고리 및 세부 항목 조회", description = "모든 테마 카테고리와 각 카테고리에 속한 세부 항목을 Map 형태로 반환합니다.")
	public ResponseEntity<CommonResponse<Map<String, List<ThemeDetailResponse>>>> getAllThemes() {
		Map<ThemeCategory, List<ThemeDetail>> allThemes = themeRecommendationService.getAllThemesWithDetails();

		Map<String, List<ThemeDetailResponse>> response = allThemes.entrySet().stream()
			.collect(java.util.stream.Collectors.toMap(
				entry -> entry.getKey().getCode(),
				entry -> entry.getValue().stream()
					.map(ThemeDetailResponse::from)
					.toList()));

		return ResponseEntity.ok(CommonResponse.of(response));
	}

	// ===== Private =====

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
