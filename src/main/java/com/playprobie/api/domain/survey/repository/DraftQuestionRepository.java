package com.playprobie.api.domain.survey.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.survey.domain.DraftQuestion;

public interface DraftQuestionRepository extends JpaRepository<DraftQuestion, Long> {

    List<DraftQuestion> findBySurveyIdOrderByOrderAsc(Long surveyId);

    void deleteBySurveyId(Long surveyId);
}
