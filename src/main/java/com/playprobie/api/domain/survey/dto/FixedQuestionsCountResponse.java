package com.playprobie.api.domain.survey.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "고정 질문 생성 응답 DTO")
public record FixedQuestionsCountResponse(

        @Schema(description = "생성된 질문 수", example = "5") @JsonProperty("count") int count) {

    public static FixedQuestionsCountResponse of(int count) {
        return new FixedQuestionsCountResponse(count);
    }
}
