package com.playprobie.api.domain.workspace.dao;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.domain.Workspace;
import com.playprobie.api.domain.workspace.domain.WorkspaceMember;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

	@Query("SELECT wm FROM WorkspaceMember wm JOIN FETCH wm.workspace WHERE wm.user = :user")
	List<WorkspaceMember> findAllByUserWithWorkspace(@Param("user")
	User user);

	@Query("SELECT wm FROM WorkspaceMember wm JOIN FETCH wm.user WHERE wm.workspace = :workspace")
	List<WorkspaceMember> findAllByWorkspaceWithUser(@Param("workspace")
	Workspace workspace);

	boolean existsByWorkspaceAndUser(Workspace workspace, User user);

	Optional<WorkspaceMember> findByWorkspaceAndUser(Workspace workspace, User user);

	// Check if a user has access to a workspace by workspaceId and user
	boolean existsByWorkspaceIdAndUser(Long workspaceId, User user);

	@Query("SELECT CASE WHEN COUNT(wm) > 0 THEN true ELSE false END FROM WorkspaceMember wm WHERE wm.workspace.id = :workspaceId AND wm.user.id = :userId")
	boolean existsByWorkspaceIdAndUserId(@Param("workspaceId")
	Long workspaceId, @Param("userId")
	Long userId);
}
