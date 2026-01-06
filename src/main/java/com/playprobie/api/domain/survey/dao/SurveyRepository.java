package com.playprobie.api.domain.survey.dao;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.survey.domain.Survey;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    java.util.List<Survey> findByGameId(Long gameId);

    java.util.List<Survey> findByGameUuid(java.util.UUID gameUuid);

    long countByGameId(Long gameId);

    java.util.Optional<Survey> findByUuid(java.util.UUID uuid);
}
