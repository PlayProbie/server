package com.playprobie.api.domain.interview.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.interview.domain.InterviewLog;

public interface InterviewLogRepository extends JpaRepository<InterviewLog, Long> {

    List<InterviewLog> findBySessionIdOrderByTurnNumAsc(Long sessionId);
}
