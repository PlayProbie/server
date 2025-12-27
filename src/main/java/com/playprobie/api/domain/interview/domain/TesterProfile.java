package com.playprobie.api.domain.interview.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TesterProfile {
	@Column(name = "tester_id")
	private String testerId;

	@Column(name = "tester_age_group")
	private String ageGroup;

	@Column(name = "tester_gender")
	private String gender;

	@Column(name = "tester_prefer_genre")
	private String preferGenre;

	@Builder
	public TesterProfile(String testerId, String ageGroup, String gender, String preferGenre) {
		this.testerId = testerId;
		this.ageGroup = ageGroup;
		this.gender = gender;
		this.preferGenre = preferGenre;
	}

	/**
	 * 비회원 테스터 프로필 생성
	 * testerId를 자동으로 UUID로 할당합니다.
	 *
	 * @param ageGroup    연령대 (20s, 30s 등)
	 * @param gender      성별 (M/F)
	 * @param preferGenre 선호 장르
	 * @return 새 TesterProfile 인스턴스
	 */
	public static TesterProfile createAnonymous(String ageGroup, String gender, String preferGenre) {
		return TesterProfile.builder()
				.testerId(UUID.randomUUID().toString())
				.ageGroup(ageGroup)
				.gender(gender)
				.preferGenre(preferGenre)
				.build();
	}
}