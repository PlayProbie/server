package com.playprobie.api.domain.auth.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

public class EmailDuplicateException extends BusinessException {

    public EmailDuplicateException() {
        super(ErrorCode.EMAIL_DUPLICATE);
    }

    public EmailDuplicateException(String email) {
        super("이미 사용 중인 이메일입니다: " + email, ErrorCode.EMAIL_DUPLICATE);
    }
}
