package com.playprobie.api.domain.survey.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.EntityNotFoundException;

public class QuestionNotFoundException extends EntityNotFoundException {
	public QuestionNotFoundException() {
		super(ErrorCode.QUESTION_NOT_FOUND);
	}
}
