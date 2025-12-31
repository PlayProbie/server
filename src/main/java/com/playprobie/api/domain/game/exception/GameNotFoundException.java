package com.playprobie.api.domain.game.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.EntityNotFoundException;

//TODO: 공통 Exception으로 빼기
public class GameNotFoundException extends EntityNotFoundException {
    public GameNotFoundException() {
        super(ErrorCode.GAME_NOT_FOUND);
    }
}
