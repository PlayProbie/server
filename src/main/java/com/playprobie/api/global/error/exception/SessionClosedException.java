package com.playprobie.api.global.error.exception;

import com.playprobie.api.global.error.ErrorCode;

public class SessionClosedException extends BusinessException {
	public SessionClosedException() {
		super(ErrorCode.SURVEY_SESSION_CLOSED);
	}
}
