package com.playprobie.api.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MessageRequest(
        @JsonProperty("turn_num") Integer turnNum,

        @JsonProperty("answer_text") String answerText) {
}
