package com.playprobie.api.domain.survey.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.EntityNotFoundException;

public class SurveyNotFoundException extends EntityNotFoundException {
    public SurveyNotFoundException() {
        super(ErrorCode.SURVEY_NOT_FOUND);
    }
}
