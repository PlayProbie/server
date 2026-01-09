package com.playprobie.api.domain.streaming.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
