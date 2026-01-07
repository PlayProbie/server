package com.playprobie.api.domain.streaming.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

/**
 * StreamClass와 OS 타입이 호환되지 않을 때 발생하는 예외.
 */
public class StreamClassIncompatibleException extends BusinessException {

	public StreamClassIncompatibleException() {
		super(ErrorCode.INVALID_INPUT_VALUE);
	}

	public StreamClassIncompatibleException(String streamClass, String osType) {
		super(String.format("StreamClass '%s'는 OS 타입 '%s'와 호환되지 않습니다.", streamClass, osType),
			ErrorCode.INVALID_INPUT_VALUE);
	}
}
