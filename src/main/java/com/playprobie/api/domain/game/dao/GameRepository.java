package com.playprobie.api.domain.game.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.game.domain.Game;

public interface GameRepository extends JpaRepository<Game, Long> {
}
