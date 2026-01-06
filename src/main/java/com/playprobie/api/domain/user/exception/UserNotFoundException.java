package com.playprobie.api.domain.user.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.EntityNotFoundException;

public class UserNotFoundException extends EntityNotFoundException {

    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }

    public UserNotFoundException(Long userId) {
        super("User not found with id: " + userId, ErrorCode.USER_NOT_FOUND);
    }

    public UserNotFoundException(String email) {
        super("User not found with email: " + email, ErrorCode.USER_NOT_FOUND);
    }
}
