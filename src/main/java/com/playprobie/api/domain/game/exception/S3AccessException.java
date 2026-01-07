package com.playprobie.api.domain.game.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

public class S3AccessException extends BusinessException {
	public S3AccessException() {
		super(ErrorCode.S3_ACCESS_ERROR);
	}
}
