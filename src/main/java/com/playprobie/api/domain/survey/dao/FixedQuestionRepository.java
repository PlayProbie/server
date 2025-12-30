package com.playprobie.api.domain.survey.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.playprobie.api.domain.survey.domain.FixedQuestion;
import com.playprobie.api.domain.survey.domain.QuestionStatus;

public interface FixedQuestionRepository extends JpaRepository<FixedQuestion, Long> {

    Optional<FixedQuestion> findFirstBySurveyIdOrderByOrderAsc(Long surveyId);

    List<FixedQuestion> findBySurveyIdOrderByOrderAsc(Long surveyId);

    List<FixedQuestion> findBySurveyIdAndStatusOrderByOrderAsc(Long surveyId, QuestionStatus status);

    void deleteBySurveyId(Long surveyId);

    /**
     * N+1 최적화: 여러 설문의 첫 번째 질문 일괄 조회
     */
    @Query("""
            SELECT fq FROM FixedQuestion fq
            WHERE fq.surveyId IN :surveyIds
            AND fq.order = (
            	SELECT MIN(fq2.order) FROM FixedQuestion fq2
            	WHERE fq2.surveyId = fq.surveyId
            )
            """)
    List<FixedQuestion> findFirstQuestionsBySurveyIds(@Param("surveyIds") Set<Long> surveyIds);

    List<FixedQuestion> findAllByIdIn(Set<Long> ids);
}
