package com.playprobie.api.domain.game.dao;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.game.domain.GameBuild;

public interface GameBuildRepository extends JpaRepository<GameBuild, Long> {
	java.util.Optional<GameBuild> findByUuid(UUID uuid);

	java.util.List<GameBuild> findByGameUuidOrderByCreatedAtDesc(UUID gameUuid);
}
