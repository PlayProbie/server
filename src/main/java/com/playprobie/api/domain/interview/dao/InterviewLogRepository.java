package com.playprobie.api.domain.interview.dao;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.interview.domain.InterviewLog;

public interface InterviewLogRepository extends JpaRepository<InterviewLog, Long> {

    List<InterviewLog> findBySessionIdOrderByTurnNumAsc(Long sessionId);

    List<InterviewLog> findBySessionUuidOrderByTurnNumAsc(UUID sessionUuid);

    /**
     * 특정 세션 + 고정질문에서 최대 turnNum 조회 (꼬리질문 순번 계산용)
     */
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(MAX(il.turnNum), 0) FROM InterviewLog il " +
            "WHERE il.session.id = :sessionId AND il.fixedQuestionId = :fixedQId")
    Integer findMaxTurnNumBySessionIdAndFixedQId(
            @org.springframework.data.repository.query.Param("sessionId") Long sessionId,
            @org.springframework.data.repository.query.Param("fixedQId") Long fixedQId);

    /**
     * 꼬리질문 응답 업데이트를 위한 조회
     */
    java.util.Optional<InterviewLog> findBySessionIdAndFixedQuestionIdAndTurnNum(
            Long sessionId, Long fixedQuestionId, Integer turnNum);
}
