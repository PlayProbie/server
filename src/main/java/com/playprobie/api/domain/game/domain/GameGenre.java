package com.playprobie.api.domain.game.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게임 장르 대분류
 */
@Getter
@RequiredArgsConstructor
public enum GameGenre {

    SHOOTER("슈팅"),
    STRATEGY("전략"),
    RPG("RPG"),
    SPORTS("스포츠"),
    SIMULATION("시뮬레이션"),
    CASUAL("캐주얼");

    private final String displayName;
}
