package com.playprobie.api.domain.survey.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

public class QuestionAlreadyConfirmedException extends BusinessException {
    public QuestionAlreadyConfirmedException() {
        super(ErrorCode.QUESTION_ALREADY_CONFIRMED);
    }
}
