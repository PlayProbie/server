package com.playprobie.api.domain.game.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게임 장르 대분류
 */
@Getter
@RequiredArgsConstructor
public enum GameGenre {

    SHOOTER("슈팅", "shooter"),
    STRATEGY("전략", "strategy"),
    RPG("RPG", "rpg"),
    SPORTS("스포츠", "sports"),
    SIMULATION("시뮬레이션", "simulation"),
    CASUAL("캐주얼", "casual");

    private final String displayName;
    private final String code;
}
