package com.playprobie.api.domain.game.dao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.game.domain.Game;

public interface GameRepository extends JpaRepository<Game, Long> {
	Optional<Game> findByUuid(UUID uuid);

	Optional<Game> findByName(String name);

	List<Game> findAllByName(String name);

	List<Game> findByWorkspaceUuid(UUID workspaceUuid);

	@org.springframework.data.jpa.repository.Query("SELECT g FROM Game g JOIN g.workspace w JOIN w.members m WHERE m.user.id = :userId")
	List<Game> findByWorkspace_Members_User_Id(@org.springframework.data.repository.query.Param("userId")
	Long userId);
}
