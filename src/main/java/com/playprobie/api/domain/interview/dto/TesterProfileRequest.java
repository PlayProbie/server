package com.playprobie.api.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.interview.domain.TesterProfile;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "테스터 프로필 요청 DTO")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TesterProfileRequest {

	@Schema(description = "연령대", example = "20s")
	@JsonProperty("age_group")
	private String ageGroup;

	@Schema(description = "성별 (MALE, FEMALE, OTHER)", example = "MALE")
	@JsonProperty("gender")
	private String gender;

	@Schema(description = "선호 게임 장르", example = "RPG")
	@JsonProperty("prefer_genre")
	private String preferGenre;

	public TesterProfile toEntity() {
		return TesterProfile.createAnonymous(ageGroup, gender, preferGenre);
	}
}
