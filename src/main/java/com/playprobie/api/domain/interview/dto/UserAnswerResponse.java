package com.playprobie.api.domain.interview.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

public record UserAnswerResponse(
        boolean accepted,

        @JsonProperty("saved_log") SavedLog savedLog) {
    public record SavedLog(
            @JsonProperty("turn_num") Integer turnNum,

            @JsonProperty("q_type") String qType,

            @JsonProperty("fixed_q_id") Long fixedQId,

            @JsonProperty("question_text") String questionText,

            @JsonProperty("answer_text") String answerText,

            @JsonProperty("answered_at") @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") OffsetDateTime answeredAt) {
    }

    public static UserAnswerResponse of(Integer turnNum, String qType, Long fixedQId,
            String questionText, String answerText) {
        return new UserAnswerResponse(
                true,
                new SavedLog(turnNum, qType, fixedQId, questionText, answerText,
                        OffsetDateTime.now(ZoneId.of("Asia/Seoul"))));
    }
}
