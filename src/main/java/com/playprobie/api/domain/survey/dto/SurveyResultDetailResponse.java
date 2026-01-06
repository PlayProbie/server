package com.playprobie.api.domain.survey.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.playprobie.api.domain.interview.domain.QuestionType;
import com.playprobie.api.domain.interview.domain.SessionStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "설문 결과 상세 응답 DTO")
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SurveyResultDetailResponse {

    @Schema(description = "세션 정보")
    private SessionInfo session;

    @Schema(description = "고정 질문별 그룹화된 응답")
    private List<FixedQuestionGroup> byFixedQuestion;

    @Schema(description = "세션 정보")
    @Getter
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class SessionInfo {
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

        @Schema(description = "종료 일시", example = "2024-01-01T10:30:00+09:00", type = "string")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Seoul")
        private OffsetDateTime endedAt;
    }

    @Schema(description = "고정 질문 그룹")
    @Getter
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class FixedQuestionGroup {
        @Schema(description = "고정 질문 내용", example = "게임 그래픽은 어떠셨나요?")
        private String fixedQuestion;

        @Schema(description = "해당 질문에 대한 응답 발췌 목록")
        private List<ExcerptItem> excerpt;
    }

    @Schema(description = "응답 발췌 항목")
    @Getter
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ExcerptItem {
        @Schema(description = "질문 유형 (FIXED, AI)", example = "FIXED")
        private QuestionType qType;

        @Schema(description = "질문 텍스트", example = "게임 그래픽은 어떠셨나요?")
        private String questionText;

        @Schema(description = "응답 텍스트", example = "그래픽이 매우 아름다웠습니다.")
        private String answerText;
    }
}
