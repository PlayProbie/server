package com.playprobie.api.domain.workspace.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.dao.WorkspaceRepository;
import com.playprobie.api.domain.workspace.domain.Workspace;
import com.playprobie.api.domain.workspace.dto.CreateWorkspaceRequest;
import com.playprobie.api.domain.workspace.dto.UpdateWorkspaceRequest;
import com.playprobie.api.domain.workspace.dto.WorkspaceResponse;
import com.playprobie.api.domain.workspace.exception.WorkspaceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    @Transactional
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, User owner) {
        Workspace workspace = Workspace.create(owner, request.name(), request.description());
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return WorkspaceResponse.simpleFrom(savedWorkspace);
    }

    public List<WorkspaceResponse> getWorkspaces(User owner) {
        List<Workspace> workspaces = workspaceRepository.findAll();
        // TODO: Filter by owner when authentication is fully implemented
        return workspaces.stream()
                .map(WorkspaceResponse::from)
                .toList();
    }

    public WorkspaceResponse getWorkspace(UUID workspaceUuid) {
        Workspace workspace = findByUuid(workspaceUuid);
        return WorkspaceResponse.from(workspace);
    }

    public Workspace getWorkspaceEntity(UUID workspaceUuid) {
        return findByUuid(workspaceUuid);
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(UUID workspaceUuid, UpdateWorkspaceRequest request) {
        Workspace workspace = findByUuid(workspaceUuid);
        workspace.update(request.name(), request.profileImageUrl(), request.description());
        return WorkspaceResponse.simpleFrom(workspace);
    }

    @Transactional
    public void deleteWorkspace(UUID workspaceUuid) {
        Workspace workspace = findByUuid(workspaceUuid);
        workspaceRepository.delete(workspace);
    }

    private Workspace findByUuid(UUID uuid) {
        return workspaceRepository.findByUuid(uuid)
                .orElseThrow(WorkspaceNotFoundException::new);
    }
}
