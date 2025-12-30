package com.playprobie.api.global.error.exception;

import com.playprobie.api.global.error.ErrorCode;

public class SessionNotFoundException extends BusinessException {
	public SessionNotFoundException() {
		super(ErrorCode.SURVEY_SESSION_NOT_FOUND);
	}
}
