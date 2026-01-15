package com.playprobie.api.domain.survey.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuestionStatus {
	DRAFT("draft", "임시"),
	CONFIRMED("confirmed", "확정");

	private final String code;
	private final String description;
}
