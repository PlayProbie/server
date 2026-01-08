package com.playprobie.api.global.error.exception;

import com.playprobie.api.global.error.ErrorCode;

public class EntityNotFoundException extends BusinessException {

	public EntityNotFoundException() {
		super(ErrorCode.ENTITY_NOT_FOUND);
	}

	public EntityNotFoundException(ErrorCode errorCode) {
		super(errorCode);
	}

	public EntityNotFoundException(String message, ErrorCode errorCode) {
		super(message, errorCode);
	}
}
