package com.playprobie.api.domain.interview.dto.common;

import com.playprobie.api.domain.interview.domain.InterviewLog;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Excerpt {
	private int turnNum;
	private String qType;
	private String questionText;
	private String answerText;

	public static Excerpt from(InterviewLog log) {
		return Excerpt.builder()
			.turnNum(log.getTurnNum())
			.qType(log.getType().name())
			.questionText(log.getQuestionText())
			.answerText(log.getAnswerText())
			.build();
	}
}
