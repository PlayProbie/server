package com.playprobie.api.domain.workspace.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.dao.WorkspaceMemberRepository;
import com.playprobie.api.domain.workspace.domain.Workspace;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceSecurityManager {

    private final WorkspaceMemberRepository workspaceMemberRepository;

    /**
     * Validate if the user has read access to the workspace.
     * Currently checks for simple membership.
     *
     * @param workspace target workspace
     * @param user      requesting user
     * @throws BusinessException if access is denied (W002)
     */
    public void validateReadAccess(Workspace workspace, User user) {
        validateMembership(workspace, user);
    }

    /**
     * Validate if the user has write access to the workspace.
     * Currently checks for simple membership.
     * Can be extended to check for specific roles (OWNER, ADMIN) in the future.
     *
     * @param workspace target workspace
     * @param user      requesting user
     * @throws BusinessException if access is denied (W002)
     */
    public void validateWriteAccess(Workspace workspace, User user) {
        validateMembership(workspace, user);
    }

    private void validateMembership(Workspace workspace, User user) {
        if (workspace == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }

        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), user.getId())) {
            throw new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND);
        }
    }
}
