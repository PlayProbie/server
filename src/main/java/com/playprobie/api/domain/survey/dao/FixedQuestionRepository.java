package com.playprobie.api.domain.survey.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.survey.domain.FixedQuestion;
import com.playprobie.api.domain.survey.domain.QuestionStatus;

public interface FixedQuestionRepository extends JpaRepository<FixedQuestion, Long> {

    List<FixedQuestion> findBySurveyIdOrderByOrderAsc(Long surveyId);

    List<FixedQuestion> findBySurveyIdAndStatusOrderByOrderAsc(Long surveyId, QuestionStatus status);

    void deleteBySurveyId(Long surveyId);
}
