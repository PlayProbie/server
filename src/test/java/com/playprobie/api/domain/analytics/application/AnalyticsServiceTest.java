package com.playprobie.api.domain.analytics.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;

import com.playprobie.api.domain.analytics.dao.QuestionResponseAnalysisRepository;
import com.playprobie.api.domain.analytics.domain.QuestionResponseAnalysis;
import com.playprobie.api.domain.interview.dao.InterviewLogRepository;
import com.playprobie.api.domain.survey.dao.FixedQuestionRepository;
import com.playprobie.api.domain.survey.domain.FixedQuestion;
import com.playprobie.api.infra.ai.AiClient;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

        @Mock
        private InterviewLogRepository interviewLogRepository;

        @Mock
        private QuestionResponseAnalysisRepository questionResponseAnalysisRepository;

        @Mock
        private FixedQuestionRepository fixedQuestionRepository;

        @Mock
        private AiClient aiClient;

        @InjectMocks
        private AnalyticsService analyticsService;

        @Test
        @DisplayName("캐시(첫번째 질문)가 유효하면 전체 캐시 반환")
        void returnCachedResultWhenFirstQuestionValid() {
                // Given
                Long surveyId = 1L;
                Long firstQId = 101L;
                Long secondQId = 102L;

                // 질문 목록 Mock (순서 중요)
                FixedQuestion q1 = mock(FixedQuestion.class);
                when(q1.getId()).thenReturn(firstQId);
                FixedQuestion q2 = mock(FixedQuestion.class); // ID만 필요

                when(fixedQuestionRepository.findBySurveyIdOrderByOrderAsc(surveyId))
                                .thenReturn(List.of(q1, q2));

                // 첫번째 질문 답변 수 Mock (Freshness Check용)
                when(interviewLogRepository.countByFixedQuestionIdAndAnswerTextIsNotNull(firstQId)).thenReturn(10);

                // 첫번째 질문 캐시 Mock (Valid: processed(10) >= current(10))
                QuestionResponseAnalysis cached1 = new QuestionResponseAnalysis(firstQId, surveyId, "json1", 10);

                when(questionResponseAnalysisRepository.findByFixedQuestionId(firstQId))
                                .thenReturn(Optional.of(cached1));

                // 전체 캐시 반환 Mock
                QuestionResponseAnalysis cached2 = new QuestionResponseAnalysis(secondQId, surveyId, "json2", 10);

                when(questionResponseAnalysisRepository.findAllBySurveyId(surveyId))
                                .thenReturn(List.of(cached1, cached2));

                // When
                StepVerifier.create(analyticsService.getSurveyAnalysis(surveyId))
                                .expectNextMatches(w -> w.fixedQuestionId().equals(firstQId)
                                                && "json1".equals(w.resultJson()))
                                .expectNextMatches(w -> w.fixedQuestionId().equals(secondQId)
                                                && "json2".equals(w.resultJson()))
                                .verifyComplete();

                // Then
                // AI 호출 없어야 함
                verify(aiClient, never()).streamQuestionAnalysis(anyLong(), anyLong());
        }

        @Test
        @DisplayName("캐시(첫번째 질문)가 낡았으면 전체 재분석")
        void analyzeAllWhenFirstQuestionStale() {
                // Given
                Long surveyId = 1L;
                Long firstQId = 101L;
                Long secondQId = 102L;

                FixedQuestion q1 = mock(FixedQuestion.class);
                when(q1.getId()).thenReturn(firstQId);
                FixedQuestion q2 = mock(FixedQuestion.class);
                when(q2.getId()).thenReturn(secondQId);

                when(fixedQuestionRepository.findBySurveyIdOrderByOrderAsc(surveyId))
                                .thenReturn(List.of(q1, q2));

                // 첫번째 질문 답변 수 (15) > 캐시 처리 수 (10) -> Stale
                when(interviewLogRepository.countByFixedQuestionIdAndAnswerTextIsNotNull(firstQId)).thenReturn(15);
                QuestionResponseAnalysis staleCached1 = new QuestionResponseAnalysis(firstQId, surveyId, "old1", 10);

                when(questionResponseAnalysisRepository.findByFixedQuestionId(firstQId))
                                .thenReturn(Optional.of(staleCached1));

                // 재분석 시 각 질문에 대해 count 조회 및 AI 호출
                // q1 재분석
                when(interviewLogRepository.countByFixedQuestionIdAndAnswerTextIsNotNull(firstQId)).thenReturn(15);
                // q2 재분석 (답변수 조회)
                when(interviewLogRepository.countByFixedQuestionIdAndAnswerTextIsNotNull(secondQId)).thenReturn(5);

                // AI Mocking
                ServerSentEvent<String> sse1 = ServerSentEvent.<String>builder().event("done").data("new1").build();
                when(aiClient.streamQuestionAnalysis(surveyId, firstQId)).thenReturn(Flux.just(sse1));

                ServerSentEvent<String> sse2 = ServerSentEvent.<String>builder().event("done").data("new2").build();
                when(aiClient.streamQuestionAnalysis(surveyId, secondQId)).thenReturn(Flux.just(sse2));

                // When
                StepVerifier.create(analyticsService.getSurveyAnalysis(surveyId))
                                .expectNextMatches(w -> w.fixedQuestionId().equals(firstQId)
                                                && "new1".equals(w.resultJson()))
                                .expectNextMatches(w -> w.fixedQuestionId().equals(secondQId)
                                                && "new2".equals(w.resultJson()))
                                .verifyComplete();

                // Then
                verify(aiClient, times(1)).streamQuestionAnalysis(surveyId, firstQId);
                verify(aiClient, times(1)).streamQuestionAnalysis(surveyId, secondQId);
                verify(questionResponseAnalysisRepository, times(2)).save(any(QuestionResponseAnalysis.class)); // save or update
                                                                                                      // logic
                // triggers
                // save in logic
        }
}
