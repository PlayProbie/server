package com.playprobie.api.domain.workspace.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.workspace.domain.Workspace;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    Optional<Workspace> findByName(String name);

    boolean existsByName(String name);
}
