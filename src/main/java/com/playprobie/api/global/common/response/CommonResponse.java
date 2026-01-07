package com.playprobie.api.global.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 공통 API 응답 래퍼.
 * <p>
 * 모든 API 응답을 {@code { "result": ... }} 형태로 감싸서 일관된 응답 구조를 제공합니다.
 * </p>
 */
@Schema(description = "공통 API 응답 래퍼")
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CommonResponse<T> {

    @Schema(description = "API 응답 데이터")
    private final T result;

    public static <T> CommonResponse<T> of(T result) {
        return new CommonResponse<>(result);
    }
}