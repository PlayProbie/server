package com.playprobie.api.domain.survey.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.survey.domain.Survey;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    List<Survey> findByGameId(Long gameId);
}
