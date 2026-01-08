package com.playprobie.api.domain.auth.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

public class InvalidCredentialsException extends BusinessException {

	public InvalidCredentialsException() {
		super(ErrorCode.INVALID_CREDENTIALS);
	}
}
