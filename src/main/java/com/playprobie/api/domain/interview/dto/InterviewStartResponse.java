package com.playprobie.api.domain.interview.dto;

import com.playprobie.api.domain.interview.dto.common.SessionInfo;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class InterviewStartResponse {
	private SessionInfo session;
	private String sseUrl;
}
