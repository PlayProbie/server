package com.playprobie.api.domain.game.domain;

import com.playprobie.api.global.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "game_id")
	private Long id;

	@Column(name = "game_name", nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "game_genre")
	private GameGenre genre;

	@Column(name = "game_context", columnDefinition = "TEXT")
	private String context;

	@Builder
	public Game(String name, GameGenre genre, String context) {
		this.name = name;
		this.genre = genre;
		this.context = context;
	}

	public void update(String name, GameGenre genre, String context) {
		this.name = name;
		this.genre = genre;
		this.context = context;
	}

	public void updateContext(String context) {
		this.context = context;
	}
}
