package com.playprobie.api.domain.streaming.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.EntityNotFoundException;

/**
 * StreamingResource를 찾을 수 없을 때 발생하는 예외.
 */
public class StreamingResourceNotFoundException extends EntityNotFoundException {

	public StreamingResourceNotFoundException() {
		super(ErrorCode.STREAMING_RESOURCE_NOT_FOUND);
	}

	public StreamingResourceNotFoundException(Long surveyId) {
		super("surveyId: " + surveyId, ErrorCode.STREAMING_RESOURCE_NOT_FOUND);
	}
}
