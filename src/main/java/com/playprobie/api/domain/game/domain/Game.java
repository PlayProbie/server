package com.playprobie.api.domain.game.domain;

import java.util.ArrayList;
import java.util.List;

import com.playprobie.api.global.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

	@Column(name = "game_genre", columnDefinition = "TEXT")
	private String genresJson;

	@Column(name = "game_context", columnDefinition = "TEXT")
	private String context;

	@Builder
	public Game(String name, List<GameGenre> genres, String context) {
		this.name = name;
		this.genresJson = convertGenresToJson(genres);
		this.context = context;
	}

	public List<GameGenre> getGenres() {
		if (genresJson == null || genresJson.isBlank()) {
			return new ArrayList<>();
		}
		List<GameGenre> result = new ArrayList<>();
		String cleaned = genresJson.replace("[", "").replace("]", "").replace("\"", "");
		for (String code : cleaned.split(",")) {
			String trimmed = code.trim();
			for (GameGenre g : GameGenre.values()) {
				if (g.getCode().equals(trimmed)) {
					result.add(g);
					break;
				}
			}
		}
		return result;
	}

	private String convertGenresToJson(List<GameGenre> genres) {
		if (genres == null || genres.isEmpty()) {
			return "[]";
		}
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < genres.size(); i++) {
			sb.append("\"").append(genres.get(i).getCode()).append("\"");
			if (i < genres.size() - 1) {
				sb.append(",");
			}
		}
		sb.append("]");
		return sb.toString();
	}

	public void update(String name, List<GameGenre> genres, String context) {
		this.name = name;
		this.genresJson = convertGenresToJson(genres);
		this.context = context;
	}

	public void updateContext(String context) {
		this.context = context;
	}
}
