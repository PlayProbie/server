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

        // 세션의 모든 로그를 fixedQuestionId별로 정렬하여 조회 (임베딩용)
        List<InterviewLog> findBySessionUuidOrderByFixedQuestionIdAscTurnNumAsc(UUID sessionUuid);

        // 특정 고정 질문에 대한 총 답변 개수 (NULL 제외)
        int countByFixedQuestionIdAndAnswerTextIsNotNull(Long fixedQuestionId);

        // 특정 세션 + 고정질문의 모든 로그 조회 (대표 답변 조회용)
        List<InterviewLog> findBySessionIdAndFixedQuestionIdOrderByTurnNumAsc(Long sessionId, Long fixedQuestionId);

        // UUID 버전 (Analytics 대표 답변 조회용)
        @org.springframework.data.jpa.repository.Query("SELECT il FROM InterviewLog il " +
                        "WHERE il.session.uuid = :sessionUuid AND il.fixedQuestionId = :fixedQuestionId " +
                        "ORDER BY il.turnNum ASC")
        List<InterviewLog> findBySessionUuidAndFixedQuestionIdOrderByTurnNumAsc(
                        @org.springframework.data.repository.query.Param("sessionUuid") UUID sessionUuid,
                        @org.springframework.data.repository.query.Param("fixedQuestionId") Long fixedQuestionId);
}
