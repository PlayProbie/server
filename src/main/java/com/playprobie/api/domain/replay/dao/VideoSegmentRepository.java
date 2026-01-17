package com.playprobie.api.domain.replay.dao;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.replay.domain.VideoSegment;

/**
 * 영상 세그먼트 Repository
 */
public interface VideoSegmentRepository extends JpaRepository<VideoSegment, Long> {

	/**
	 * UUID로 세그먼트 조회
	 */
	Optional<VideoSegment> findByUuid(UUID uuid);

	/**
	 * 세션 ID와 시퀀스로 세그먼트 조회
	 */
	Optional<VideoSegment> findBySessionIdAndSequence(Long sessionId, Integer sequence);
}
