package com.playprobie.api.domain.replay.dao;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.replay.domain.AnalysisTag;

/**
 * 분석 태그 Repository
 */
public interface AnalysisTagRepository extends JpaRepository<AnalysisTag, Long> {

	/**
	 * 세션 ID로 질문하지 않은 태그 목록 조회
	 */
	List<AnalysisTag> findBySessionIdAndIsAskedFalse(Long sessionId);

	/**
	 * 세션 UUID로 질문하지 않은 태그 목록 조회
	 */
	List<AnalysisTag> findBySessionUuidAndIsAskedFalse(UUID sessionUuid);

	/**
	 * 세션 ID로 모든 태그 조회
	 */
	List<AnalysisTag> findBySessionId(Long sessionId);

	/**
	 * 세션 ID로 선택된 태그 중 아직 질문하지 않은 태그 조회
	 */
	List<AnalysisTag> findBySessionIdAndIsSelectedTrueAndIsAskedFalse(Long sessionId);

	/**
	 * 세션 UUID로 선택된 태그 중 아직 질문하지 않은 태그 조회
	 */
	List<AnalysisTag> findBySessionUuidAndIsSelectedTrueAndIsAskedFalse(UUID sessionUuid);
}
