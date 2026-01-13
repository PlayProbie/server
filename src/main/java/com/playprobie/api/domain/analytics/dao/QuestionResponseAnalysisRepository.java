package com.playprobie.api.domain.analytics.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.analytics.domain.QuestionResponseAnalysis;

public interface QuestionResponseAnalysisRepository
	extends JpaRepository<QuestionResponseAnalysis, Long> {

	Optional<QuestionResponseAnalysis> findByFixedQuestionId(Long fixedQuestionId);

	List<QuestionResponseAnalysis> findAllBySurveyId(Long surveyId);

	long countBySurveyIdAndStatus(Long surveyId, QuestionResponseAnalysis.AnalysisStatus status);
}
