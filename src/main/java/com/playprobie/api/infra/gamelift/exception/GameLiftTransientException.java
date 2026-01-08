package com.playprobie.api.infra.gamelift.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

public class GameLiftTransientException extends BusinessException {
	public GameLiftTransientException(ErrorCode errorCode) {
		super(errorCode);
	}
}
