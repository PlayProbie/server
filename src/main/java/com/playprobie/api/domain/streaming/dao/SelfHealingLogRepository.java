package com.playprobie.api.domain.streaming.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.playprobie.api.domain.streaming.domain.SelfHealingLog;

/**
 * SelfHealingLog Repository.
 */
public interface SelfHealingLogRepository extends JpaRepository<SelfHealingLog, Long> {

	/**
	 * Resource ID로 Self-Healing 로그를 조회합니다.
	 */
	Optional<SelfHealingLog> findByResourceId(Long resourceId);

	/**
	 * 오래된 로그를 삭제합니다 (정리 작업용).
	 *
	 * @param retentionHours 보관 기간 (시간)
	 * @return 삭제된 레코드 수
	 */
	@Modifying
	@Query("DELETE FROM SelfHealingLog s WHERE s.lastAttemptAt < :threshold")
	int deleteOldLogs(@Param("threshold")
	java.time.LocalDateTime threshold);
}
