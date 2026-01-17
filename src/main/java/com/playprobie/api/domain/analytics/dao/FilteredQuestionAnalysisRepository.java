package com.playprobie.api.domain.analytics.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.analytics.domain.FilteredQuestionAnalysis;

public interface FilteredQuestionAnalysisRepository
	extends JpaRepository<FilteredQuestionAnalysis, Long> {

	Optional<FilteredQuestionAnalysis> findByFixedQuestionIdAndFilterSignature(Long fixedQuestionId,
		String filterSignature);
}
