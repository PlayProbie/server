package com.playprobie.api.global.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 공통 API 응답 래퍼.
 * <p>
 * 모든 API 응답을 {@code { "result": ... }} 형태로 감싸서 일관된 응답 구조를 제공합니다.
 * </p>
 *
 * <pre>
 * // 사용 예시
 * return ResponseEntity.ok(ApiResponse.of(userResponse));
 * </pre>
 *
 * @param <T> 응답 데이터 타입
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private final T result;

    /**
     * 주어진 데이터를 ApiResponse로 감싸서 반환합니다.
     *
     * @param result 응답 데이터
     * @param <T>    응답 데이터 타입
     * @return ApiResponse 인스턴스
     */
    public static <T> ApiResponse<T> of(T result) {
        return new ApiResponse<>(result);
    }
}
