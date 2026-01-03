package com.playprobie.api.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserAnswerResponse(
                @JsonProperty("turn_num") Integer turnNum,

                @JsonProperty("q_type") String qType,

                @JsonProperty("fixed_q_id") Long fixedQId,

                @JsonProperty("question_text") String questionText,

                @JsonProperty("answer_text") String answerText) {

        public static UserAnswerResponse of(Integer turnNum, String qType, Long fixedQId,
                        String questionText, String answerText) {
                return new UserAnswerResponse(turnNum, qType, fixedQId, questionText, answerText);
        }
}
