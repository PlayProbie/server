package com.playprobie.api.domain.streaming.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.playprobie.api.domain.streaming.domain.CapacityChangeRequest;

public interface CapacityChangeRequestRepository extends JpaRepository<CapacityChangeRequest, Long> {

	Optional<CapacityChangeRequest> findByIdempotencyKey(String idempotencyKey);

	// Fetch join to avoid N+1 when accessing resource
	@Query("SELECT r FROM CapacityChangeRequest r JOIN FETCH r.resource WHERE r.id = :id")
	Optional<CapacityChangeRequest> findByIdWithResource(@Param("id")
	Long id);
}
