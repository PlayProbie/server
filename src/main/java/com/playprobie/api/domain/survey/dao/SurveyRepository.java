package com.playprobie.api.domain.survey.dao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.survey.domain.Survey;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    List<Survey> findByGameId(Long gameId);

    long countByGameId(Long gameId);

    Optional<Survey> findByUuid(UUID uuid);
}
