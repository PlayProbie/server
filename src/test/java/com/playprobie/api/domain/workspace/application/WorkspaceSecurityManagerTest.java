package com.playprobie.api.domain.workspace.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.domain.workspace.dao.WorkspaceMemberRepository;
import com.playprobie.api.domain.workspace.domain.Workspace;
import com.playprobie.api.global.error.exception.BusinessException;
import com.playprobie.api.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class WorkspaceSecurityManagerTest {

    @InjectMocks
    private WorkspaceSecurityManager securityManager;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    @DisplayName("Read Access: User is a member -> Pass")
    void validateReadAccess_Success() {
        // given
        Workspace workspace = mock(Workspace.class);
        User user = mock(User.class);

        given(workspace.getId()).willReturn(1L);
        given(user.getId()).willReturn(1L);
        given(workspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).willReturn(true);

        // when & then
        assertThatCode(() -> securityManager.validateReadAccess(workspace, user))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Read Access: User is NOT a member -> Throw W002")
    void validateReadAccess_Fail() {
        // given
        Workspace workspace = mock(Workspace.class);
        User user = mock(User.class);

        given(workspace.getId()).willReturn(1L);
        given(user.getId()).willReturn(1L);
        given(workspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> securityManager.validateReadAccess(workspace, user))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKSPACE_MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("Write Access: User is a member -> Pass")
    void validateWriteAccess_Success() {
        // given
        Workspace workspace = mock(Workspace.class);
        User user = mock(User.class);

        given(workspace.getId()).willReturn(1L);
        given(user.getId()).willReturn(1L);
        given(workspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).willReturn(true);

        // when & then
        assertThatCode(() -> securityManager.validateWriteAccess(workspace, user))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Write Access: User is NOT a member -> Throw W002")
    void validateWriteAccess_Fail() {
        // given
        Workspace workspace = mock(Workspace.class);
        User user = mock(User.class);

        given(workspace.getId()).willReturn(1L);
        given(user.getId()).willReturn(1L);
        given(workspaceMemberRepository.existsByWorkspaceIdAndUserId(1L, 1L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> securityManager.validateWriteAccess(workspace, user))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKSPACE_MEMBER_NOT_FOUND);
    }
}
