package com.playprobie.api.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserAnswerRequest {
	@JsonProperty("fixed_q_id")
	private Long fixedQId;
	@JsonProperty("turn_num")
	private Integer turnNum;
	@JsonProperty("answer_text")
	private String answerText;
	@JsonProperty("question_text")
	private String questionText;
}
