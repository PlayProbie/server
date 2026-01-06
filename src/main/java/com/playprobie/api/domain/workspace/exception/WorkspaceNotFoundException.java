package com.playprobie.api.domain.workspace.exception;

import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.EntityNotFoundException;

public class WorkspaceNotFoundException extends EntityNotFoundException {

    public WorkspaceNotFoundException() {
        super(ErrorCode.WORKSPACE_NOT_FOUND);
    }

    public WorkspaceNotFoundException(Long workspaceId) {
        super("Workspace not found with id: " + workspaceId, ErrorCode.WORKSPACE_NOT_FOUND);
    }
}
