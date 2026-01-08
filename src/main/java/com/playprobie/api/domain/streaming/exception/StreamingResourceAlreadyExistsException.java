package com.playprobie.api.domain.streaming.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

/**
 * 이미 스트리밍 리소스가 연결되어 있을 때 발생하는 예외.
 */
public class StreamingResourceAlreadyExistsException extends BusinessException {

	public StreamingResourceAlreadyExistsException() {
		super(ErrorCode.STREAMING_RESOURCE_ALREADY_EXISTS);
	}
}
