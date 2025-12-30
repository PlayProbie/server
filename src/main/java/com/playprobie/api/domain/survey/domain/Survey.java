package com.playprobie.api.domain.survey.domain;

import java.time.LocalDateTime;
import java.util.Objects;

import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.global.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "survey")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Survey extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "survey_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "game_id", nullable = false)
	private Game game;

	@Column(name = "survey_name")
	private String name;

	@Column(name = "survey_url", unique = true)
	private String surveyUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "test_purpose")
	private TestPurpose testPurpose;

	@Column(name = "start_at")
	private LocalDateTime startAt;

	@Column(name = "end_at")
	private LocalDateTime endAt;

	@Builder
	public Survey(Game game, String name, TestPurpose testPurpose, LocalDateTime startAt, LocalDateTime endAt) {
		this.game = Objects.requireNonNull(game, "Survey 생성 시 Game은 필수입니다");
		this.name = Objects.requireNonNull(name, "Survey 생성 시 name은 필수입니다");
		this.testPurpose = testPurpose;
		this.startAt = startAt;
		this.endAt = endAt;
	}

	public void assignUrl(String surveyUrl) {
		this.surveyUrl = surveyUrl;
	}

	public boolean isOpen() {
		if (startAt == null || endAt == null) {
			return false;
		}
		LocalDateTime now = LocalDateTime.now();
		return now.isAfter(startAt) && now.isBefore(endAt);
	}

	public void updateSchedule(LocalDateTime startAt, LocalDateTime endAt) {
		this.startAt = startAt;
		this.endAt = endAt;
	}
}
