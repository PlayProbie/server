package com.playprobie.api.domain.survey.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.survey.domain.FixedQuestion;

public interface FixedQuestionRepository extends JpaRepository<FixedQuestion, Long> {

    List<FixedQuestion> findBySurveyIdOrderByOrderAsc(Long surveyId);

    void deleteBySurveyId(Long surveyId);
}
