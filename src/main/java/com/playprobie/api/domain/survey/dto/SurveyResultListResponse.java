package com.playprobie.api.domain.survey.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.playprobie.api.domain.interview.domain.SessionStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "설문 결과 목록 응답 DTO")
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SurveyResultListResponse {

	private List<SessionItem> content;

	@Schema(description = "다음 페이지 커서", example = "10")
	private Long nextCursor;

	@Schema(description = "다음 페이지 존재 여부", example = "true")
	private boolean hasNext;

	@Getter
	@Builder
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public static class SessionItem {
		private java.util.UUID sessionUuid;
		private String surveyName;

		@Schema(description = "설문 UUID", example = "550e8400-e29b-41d4-a716-446655440000")
		private UUID surveyUuid;

		@Schema(description = "테스터 ID", example = "tester123")
		private String testerId;
		private SessionStatus status;
		private String firstQuestion;

		@Schema(description = "종료 일시", example = "2024-01-01T10:30:00+09:00", type = "string")
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul")
		private OffsetDateTime endedAt;
	}
}
