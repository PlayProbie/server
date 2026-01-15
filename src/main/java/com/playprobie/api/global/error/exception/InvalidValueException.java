package com.playprobie.api.global.error.exception;

import com.playprobie.api.global.error.ErrorCode;

public class InvalidValueException extends BusinessException {

	public InvalidValueException(ErrorCode errorCode) {
		super(errorCode);
	}

	public InvalidValueException(String value) {
		super(ErrorCode.INVALID_INPUT_VALUE);
	}

	public InvalidValueException(String value, ErrorCode errorCode) {
		super(errorCode);
	}
}
