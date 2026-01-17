package com.playprobie.api.domain.streaming.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.playprobie.api.domain.streaming.domain.CapacityChangeRequest;
import com.playprobie.api.domain.streaming.domain.RequestStatus;

public interface CapacityChangeRequestRepository extends JpaRepository<CapacityChangeRequest, Long> {

	// Fetch join to avoid N+1 when accessing resource
	@Query("SELECT r FROM CapacityChangeRequest r JOIN FETCH r.resource WHERE r.id = :id")
	Optional<CapacityChangeRequest> findByIdWithResource(@Param("id")
	Long id);

	@Query("SELECT r FROM CapacityChangeRequest r JOIN FETCH r.resource WHERE r.status = :status AND r.createdAt < :threshold")
	List<CapacityChangeRequest> findAllByStatusAndCreatedAtBefore(
		@Param("status")
		RequestStatus status,
		@Param("threshold")
		LocalDateTime threshold);

	/**
	 * Resource ID로 모든 CapacityChangeRequest를 삭제합니다.
	 * <p>
	 * StreamingResource 삭제 전 FK 제약조건 해결을 위해 사용.
	 */
	@Modifying
	@Query("DELETE FROM CapacityChangeRequest c WHERE c.resource.id = :resourceId")
	void deleteByResourceId(@Param("resourceId")
	Long resourceId);
}
