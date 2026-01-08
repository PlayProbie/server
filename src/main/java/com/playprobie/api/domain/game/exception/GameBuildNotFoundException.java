package com.playprobie.api.domain.game.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.EntityNotFoundException;

public class GameBuildNotFoundException extends EntityNotFoundException {
	public GameBuildNotFoundException() {
		super(ErrorCode.GAME_BUILD_NOT_FOUND);
	}
}
