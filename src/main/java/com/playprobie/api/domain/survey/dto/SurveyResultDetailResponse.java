package com.playprobie.api.domain.survey.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.playprobie.api.domain.interview.domain.QuestionType;
import com.playprobie.api.domain.interview.domain.SessionStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SurveyResultDetailResponse {

    private SessionInfo session;
    private List<FixedQuestionGroup> byFixedQuestion;

    @Getter
    @Builder
    public static class SessionInfo {
        private Long sessionId;
        private String surveyName;
        private Long surveyId;
        private String testerId;
        private SessionStatus status;
        private LocalDateTime endedAt;
    }

    @Getter
    @Builder
    public static class FixedQuestionGroup {
        private Long fixedQId;
        private String fixedQuestion;
        private List<ExcerptItem> excerpt;
    }

    @Getter
    @Builder
    public static class ExcerptItem {
        private QuestionType qType;
        private String questionText;
        private String answerText;
    }
}
