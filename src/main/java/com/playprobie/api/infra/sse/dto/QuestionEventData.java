package com.playprobie.api.infra.sse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QuestionEventData(
        @JsonProperty("fixed_q_id") Long fixedQId,

        @JsonProperty("q_type") String qType,

        @JsonProperty("question_text") String questionText,

        @JsonProperty("turn_num") Integer turnNum) {
    public static QuestionEventData ofTail(String questionText, int tailCount) {
        return new QuestionEventData(null, "TAIL", questionText, tailCount);
    }

    public static QuestionEventData ofFixed(Long fixedQId, String questionText, int turnNum) {
        return new QuestionEventData(fixedQId, "FIXED", questionText, turnNum);
    }
}
