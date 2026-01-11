package com.playprobie.api.infra.gamelift.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

public class GameLiftQuotaExceededException extends BusinessException {
	public GameLiftQuotaExceededException(ErrorCode errorCode) {
		super(errorCode);
	}

	public GameLiftQuotaExceededException(String message, ErrorCode errorCode) {
		super(message, errorCode);
	}
}
