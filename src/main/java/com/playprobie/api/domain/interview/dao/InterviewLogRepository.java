package com.playprobie.api.domain.interview.dao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.playprobie.api.domain.interview.domain.InterviewLog;
import com.playprobie.api.domain.interview.domain.SurveySession;

public interface InterviewLogRepository extends JpaRepository<InterviewLog, Long> {

    List<InterviewLog> findBySessionIdOrderByTurnNumAsc(Long sessionId);

    List<InterviewLog> findBySessionUuidOrderByTurnNumAsc(UUID sessionUuid);
}
