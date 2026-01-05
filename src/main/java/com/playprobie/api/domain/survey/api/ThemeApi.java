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
import com.playprobie.api.global.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 테마 관련 API
 */
@RestController
@RequestMapping("/themes")
@RequiredArgsConstructor
public class ThemeApi {

    private final ThemeRecommendationService themeRecommendationService;

    /**
     * 테스트 단계별 추천 테마 조회
     * GET /themes/recommendations?stage=PROTOTYPE
     */
    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<ThemeCategoryResponse>>> getRecommendedThemes(
            @RequestParam String stage) {
        TestStage testStage = parseTestStage(stage);
        List<ThemeCategory> themes = themeRecommendationService.getRecommendedThemes(testStage);

        List<ThemeCategoryResponse> response = themes.stream()
                .map(ThemeCategoryResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * 특정 테마 카테고리의 세부 항목 조회
     * GET /themes/{category}/details
     */
    @GetMapping("/{category}/details")
    public ResponseEntity<ApiResponse<List<ThemeDetailResponse>>> getThemeDetails(
            @PathVariable String category) {
        ThemeCategory themeCategory = parseThemeCategory(category);
        List<ThemeDetail> details = themeRecommendationService.getThemeDetails(themeCategory);

        List<ThemeDetailResponse> response = details.stream()
                .map(ThemeDetailResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * 모든 테마 카테고리와 세부 항목 조회
     * GET /themes/all
     */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<Map<String, List<ThemeDetailResponse>>>> getAllThemes() {
        Map<ThemeCategory, List<ThemeDetail>> allThemes = themeRecommendationService.getAllThemesWithDetails();

        Map<String, List<ThemeDetailResponse>> response = allThemes.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().getCode(),
                        entry -> entry.getValue().stream()
                                .map(ThemeDetailResponse::from)
                                .toList()));

        return ResponseEntity.ok(ApiResponse.of(response));
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

    // ===== Response DTOs =====

    public record ThemeCategoryResponse(String code, String displayName) {
        public static ThemeCategoryResponse from(ThemeCategory category) {
            return new ThemeCategoryResponse(category.getCode(), category.getDisplayName());
        }
    }

    public record ThemeDetailResponse(String code, String displayName, String category) {
        public static ThemeDetailResponse from(ThemeDetail detail) {
            return new ThemeDetailResponse(
                    detail.getCode(),
                    detail.getDisplayName(),
                    detail.getCategory().getCode());
        }
    }
}
