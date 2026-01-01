package com.playprobie.api.domain.interview.dao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.playprobie.api.domain.interview.domain.SessionStatus;
import com.playprobie.api.domain.interview.domain.SurveySession;

public interface SurveySessionRepository extends JpaRepository<SurveySession, Long> {

    Optional<SurveySession> findByUuid(UUID uuid);

    @Query("""
            SELECT COUNT(ss) FROM SurveySession ss
            JOIN ss.survey s
            WHERE s.game.id = :gameId AND ss.status = :status
            """)
    long countByGameIdAndStatus(@Param("gameId") Long gameId,
            @Param("status") SessionStatus status);

    @Query("""
            SELECT ss FROM SurveySession ss
            JOIN FETCH ss.survey s
            WHERE s.game.id = :gameId
            AND (:cursor IS NULL OR ss.id < :cursor)
            ORDER BY ss.id DESC
            """)
    List<SurveySession> findByGameIdWithCursor(
            @Param("gameId") Long gameId,
            @Param("cursor") Long cursor,
            Pageable pageable);
}
