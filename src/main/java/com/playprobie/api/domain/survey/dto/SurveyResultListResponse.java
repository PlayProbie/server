package com.playprobie.api.domain.survey.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.playprobie.api.domain.interview.domain.SessionStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SurveyResultListResponse {

    private List<SessionItem> content;
    private Long nextCursor;
    private boolean hasNext;

    @Getter
    @Builder
    public static class SessionItem {
        private Long sessionId;
        private String surveyName;
        private Long surveyId;
        private String testerId;
        private SessionStatus status;
        private String firstQuestion;
        private LocalDateTime endedAt;
    }
}
