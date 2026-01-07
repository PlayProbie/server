package com.playprobie.api.domain.user.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	Optional<User> findByProviderAndProviderId(String provider, String providerId);

	java.util.Optional<User> findByUuid(java.util.UUID uuid);
}
