package com.playprobie.api.global.error.exception;

import com.playprobie.api.global.error.ErrorCode;

public class EntityNotFoundException extends BusinessException {

    public EntityNotFoundException() {
        super(ErrorCode.ENTITY_NOT_FOUND);
    }
}