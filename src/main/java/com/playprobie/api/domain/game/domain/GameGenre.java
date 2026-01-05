package com.playprobie.api.domain.game.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게임 장르 대분류
 */
@Getter
@RequiredArgsConstructor
public enum GameGenre {

    ACTION("액션", "action"),
    ADVENTURE("어드벤처", "adventure"),
    SIMULATION("시뮬레이션", "simulation"),
    PUZZLE("퍼즐", "puzzle"),
    STRATEGY("전략", "strategy"),
    RPG("RPG", "rpg"),
    ARCADE("아케이드", "arcade"),
    HORROR("호러", "horror"),
    SHOOTER("슈팅", "shooter"),
    VISUAL_NOVEL("비주얼 노벨", "visual_novel"),
    ROGUELIKE("로그라이크", "roguelike"),
    SPORTS("스포츠", "sports"),
    RHYTHM("리듬", "rhythm"),
    FIGHTING("대전", "fighting"),
    CASUAL("캐주얼", "casual");

    private final String displayName;
    private final String code;
}
