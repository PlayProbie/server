package com.playprobie.api.infra.gamelift.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

public class GameLiftResourceNotFoundException extends BusinessException {
	public GameLiftResourceNotFoundException(ErrorCode errorCode) {
		super(errorCode);
	}
}
