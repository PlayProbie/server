package com.playprobie.api.domain.game.dao;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.game.domain.GameBuild;

public interface GameBuildRepository extends JpaRepository<GameBuild, Long> {
    Optional<GameBuild> findByUuid(UUID uuid);
}
