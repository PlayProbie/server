package com.playprobie.api.domain.survey.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.survey.domain.Survey;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    long countByGameId(Long gameId);
}
