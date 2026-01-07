package com.playprobie.api.global.error;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.validation.BindingResult;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "에러 응답 DTO")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ErrorResponse {

	@Schema(description = "에러 메시지", example = "잘못된 입력값입니다.")
	private String message;

	@Schema(description = "HTTP 상태 코드", example = "400")
	private int status;

	@Schema(description = "에러 코드", example = "C001")
	private String code;

	@Schema(description = "필드 에러 목록 (유효성 검사 실패 시)")
	private List<FieldError> errors;

	private ErrorResponse(final ErrorCode code, final List<FieldError> errors) {
		this.message = code.getMessage();
		this.status = code.getStatus();
		this.code = code.getCode();
		this.errors = errors;
	}

	private ErrorResponse(final ErrorCode code) {
		this.message = code.getMessage();
		this.status = code.getStatus();
		this.code = code.getCode();
		this.errors = new ArrayList<>();
	}

	public static ErrorResponse of(final ErrorCode code, final BindingResult bindingResult) {
		return new ErrorResponse(code, FieldError.of(bindingResult));
	}

	public static ErrorResponse of(final ErrorCode code) {
		return new ErrorResponse(code);
	}

	public static ErrorResponse of(
		final org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
		String value = e.getValue() == null ? "" : e.getValue().toString();
		List<FieldError> errors = List.of(
			new FieldError(e.getName(), value, e.getErrorCode()));
		return new ErrorResponse(ErrorCode.INVALID_TYPE_VALUE, errors);
	}

	@Schema(description = "필드 에러 상세")
	public record FieldError(
		@Schema(description = "필드명", example = "email")
		String field,
		@Schema(description = "거부된 값", example = "invalid-email")
		String value,
		@Schema(description = "에러 사유", example = "올바른 이메일 형식이 아닙니다.")
		String reason) {
		public static List<FieldError> of(final BindingResult bindingResult) {
			return bindingResult.getFieldErrors().stream()
				.map(error -> new FieldError(
					error.getField(),
					error.getRejectedValue() == null ? "" : error.getRejectedValue().toString(),
					error.getDefaultMessage()))
				.collect(Collectors.toList());
		}
	}
}
