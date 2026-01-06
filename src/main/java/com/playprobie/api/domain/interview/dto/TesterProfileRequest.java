package com.playprobie.api.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playprobie.api.domain.interview.domain.TesterProfile;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TesterProfileRequest {

    @JsonProperty("age_group")
    private String ageGroup;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("prefer_genre")
    private String preferGenre;

    public TesterProfile toEntity() {
        return TesterProfile.createAnonymous(ageGroup, gender, preferGenre);
    }
}
