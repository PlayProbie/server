package com.playprobie.api.domain.survey.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.playprobie.api.domain.interview.domain.SessionStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "설문 결과 목록 응답 DTO")
@Getter
@Builder
public class SurveyResultListResponse {

    @Schema(description = "세션 목록")
    private List<SessionItem> content;

    @Schema(description = "다음 페이지 커서", example = "10")
    private Long nextCursor;

    @Schema(description = "다음 페이지 존재 여부", example = "true")
    private boolean hasNext;

    @Schema(description = "세션 항목")
    @Getter
    @Builder
    public static class SessionItem {
        @Schema(description = "세션 UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID sessionUuid;

        @Schema(description = "설문 이름", example = "출시 전 테스트 설문")
        private String surveyName;

        @Schema(description = "설문 UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID surveyUuid;

        @Schema(description = "테스터 ID", example = "tester123")
        private String testerId;

        @Schema(description = "세션 상태 (ACTIVE, COMPLETED, TIMEOUT, ERROR)", example = "COMPLETED")
        private SessionStatus status;

        @Schema(description = "첫 번째 질문", example = "게임 그래픽은 어떠셨나요?")
        private String firstQuestion;

        @Schema(description = "종료 일시", example = "2024-01-01T10:30:00+09:00", type = "string")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul")
        private OffsetDateTime endedAt;
    }
}
