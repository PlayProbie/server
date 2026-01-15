package com.playprobie.api.domain.game.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게임 장르 대분류
 */
@Getter
@RequiredArgsConstructor
public enum GameGenre {
	ACTION("액션"),
	ADVENTURE("어드벤처"),
	SIMULATION("시뮬레이션"),
	PUZZLE("퍼즐"),
	STRATEGY("전략"),
	RPG("RPG"),
	ARCADE("아케이드"),
	HORROR("호러"),
	SHOOTER("슈팅"),
	VISUAL_NOVEL("비주얼 노벨"),
	ROGUELIKE("로그라이크"),
	SPORTS("스포츠"),
	RHYTHM("리듬"),
	FIGHTING("대전"),
	CASUAL("캐주얼");

	private final String displayName;
}
