package com.playprobie.api.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "사용자 응답 요청 DTO")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserAnswerRequest {

	@Schema(description = "고정 질문 ID", example = "1")
	@JsonProperty("fixed_q_id")
	private Long fixedQId;

	@Schema(description = "턴 번호", example = "1")
	@JsonProperty("turn_num")
	private Integer turnNum;

	@Schema(description = "사용자 응답 텍스트", example = "게임 조작법이 직관적이었습니다.")
	@JsonProperty("answer_text")
	private String answerText;

	@Schema(description = "질문 텍스트", example = "게임의 조작법은 어떠셨나요?")
	@JsonProperty("question_text")
	private String questionText;
}
