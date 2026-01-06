package com.playprobie.api.domain.workspace.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.user.dao.UserRepository;
import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.user.exception.UserNotFoundException;
import com.playprobie.api.domain.workspace.dao.WorkspaceMemberRepository;
import com.playprobie.api.domain.workspace.dao.WorkspaceRepository;
import com.playprobie.api.domain.workspace.domain.Workspace;
import com.playprobie.api.domain.workspace.domain.WorkspaceMember;
import com.playprobie.api.domain.workspace.domain.WorkspaceRole;
import com.playprobie.api.domain.workspace.dto.CreateWorkspaceRequest;
import com.playprobie.api.domain.workspace.dto.InviteMemberRequest;
import com.playprobie.api.domain.workspace.dto.UpdateWorkspaceRequest;
import com.playprobie.api.domain.workspace.dto.WorkspaceMemberResponse;
import com.playprobie.api.domain.workspace.dto.WorkspaceResponse;
import com.playprobie.api.domain.workspace.exception.WorkspaceNotFoundException;
import com.playprobie.api.global.error.exception.BusinessException;
import com.playprobie.api.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, User creator) {
        // 1. Create Workspace
        Workspace workspace = Workspace.create(request.name(), request.description());
        Workspace savedWorkspace = workspaceRepository.save(workspace);

        // 2. Create WorkspaceMember (OWNER)
        WorkspaceMember ownerMember = WorkspaceMember.builder()
                .workspace(savedWorkspace)
                .user(creator)
                .role(WorkspaceRole.OWNER)
                .build();
        workspaceMemberRepository.save(ownerMember);

        return WorkspaceResponse.simpleFrom(savedWorkspace);
    }

    public List<WorkspaceResponse> getWorkspaces(User user) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findAllByUserWithWorkspace(user);
        return memberships.stream()
                .map(membership -> WorkspaceResponse.from(membership.getWorkspace()))
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
    public WorkspaceResponse updateWorkspace(UUID workspaceUuid, UpdateWorkspaceRequest request, User actor) {
        Workspace workspace = findByUuid(workspaceUuid);
        validateOwner(workspace, actor);

        workspace.update(request.name(), request.profileImageUrl(), request.description());
        return WorkspaceResponse.simpleFrom(workspace);
    }

    @Transactional
    public void deleteWorkspace(UUID workspaceUuid, User actor) {
        Workspace workspace = findByUuid(workspaceUuid);
        validateOwner(workspace, actor);

        workspaceRepository.delete(workspace);
    }

    // --- Member Management ---

    @Transactional
    public WorkspaceMemberResponse inviteMember(UUID workspaceUuid, InviteMemberRequest request, User actor) {
        Workspace workspace = findByUuid(workspaceUuid);
        // Only existing members can invite? Or only Owner/Admin?
        // For now, let's assume any member can invite, or check if actor is member.
        // User requirements said "Owner only" for remove, but specific for Invite?
        // Let's restrict Invite to OWNER/ADMIN/EDITOR? Or just check membership.
        // Let's play safe and check if actor is a member of the workspace.
        validateMembership(workspace, actor);

        User invitee = userRepository.findByEmail(request.email())
                .orElseThrow(UserNotFoundException::new);

        if (workspaceMemberRepository.existsByWorkspaceAndUser(workspace, invitee)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE); // Using generic for now, ideally ALREADY_EXISTS
        }

        WorkspaceMember newMember = WorkspaceMember.builder()
                .workspace(workspace)
                .user(invitee)
                .role(WorkspaceRole.MEMBER) // Default role
                .build();

        WorkspaceMember savedMember = workspaceMemberRepository.save(newMember);
        return WorkspaceMemberResponse.from(savedMember);
    }

    public List<WorkspaceMemberResponse> getMembers(UUID workspaceUuid, User actor) {
        Workspace workspace = findByUuid(workspaceUuid);
        validateMembership(workspace, actor);

        List<WorkspaceMember> members = workspaceMemberRepository.findAllByWorkspaceWithUser(workspace);
        return members.stream()
                .map(WorkspaceMemberResponse::from)
                .toList();
    }

    @Transactional
    public void removeMember(UUID workspaceUuid, Long targetParamUserId, User actor) {
        // userId here is likely the User's ID (PK), not Member ID (PK). API said
        // "userId".
        Workspace workspace = findByUuid(workspaceUuid);
        validateOwner(workspace, actor); // Only Owner can remove

        User targetUser = userRepository.findById(targetParamUserId)
                .orElseThrow(UserNotFoundException::new);

        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceAndUser(workspace, targetUser)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND)); // Member not found

        if (member.isOwner()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE); // Cannot remove Owner
        }

        workspaceMemberRepository.delete(member);
    }

    // --- Helpers ---

    private Workspace findByUuid(UUID uuid) {
        return workspaceRepository.findByUuid(uuid)
                .orElseThrow(WorkspaceNotFoundException::new);
    }

    private void validateOwner(Workspace workspace, User user) {
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceAndUser(workspace, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.HANDLE_ACCESS_DENIED));

        if (member.getRole() != WorkspaceRole.OWNER) {
            throw new BusinessException(ErrorCode.HANDLE_ACCESS_DENIED);
        }
    }

    private void validateMembership(Workspace workspace, User user) {
        if (!workspaceMemberRepository.existsByWorkspaceAndUser(workspace, user)) {
            throw new BusinessException(ErrorCode.HANDLE_ACCESS_DENIED);
        }
    }
}
