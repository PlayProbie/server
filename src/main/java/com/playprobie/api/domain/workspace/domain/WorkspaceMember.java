package com.playprobie.api.domain.workspace.domain;

import java.time.LocalDateTime;

import com.playprobie.api.domain.user.domain.User;
import com.playprobie.api.global.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "workspace_member", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "workspace_id" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workspace_member_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private WorkspaceRole role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Builder
    public WorkspaceMember(User user, Workspace workspace, WorkspaceRole role) {
        this.user = user;
        this.workspace = workspace;
        this.role = role;
        this.joinedAt = LocalDateTime.now();
    }

    /**
     * 워크스페이스 생성 시 마스터 멤버 생성
     */
    public static WorkspaceMember createMaster(User user, Workspace workspace) {
        WorkspaceMember member = new WorkspaceMember(user, workspace, WorkspaceRole.MASTER);
        workspace.addMember(member);
        return member;
    }

    /**
     * 멤버 초대
     */
    public static WorkspaceMember invite(User user, Workspace workspace, WorkspaceRole role) {
        if (role == WorkspaceRole.MASTER) {
            throw new IllegalArgumentException("MASTER 역할로 초대할 수 없습니다.");
        }
        WorkspaceMember member = new WorkspaceMember(user, workspace, role);
        workspace.addMember(member);
        return member;
    }

    public void changeRole(WorkspaceRole newRole) {
        if (this.role == WorkspaceRole.MASTER) {
            throw new IllegalStateException("MASTER 역할은 변경할 수 없습니다.");
        }
        if (newRole == WorkspaceRole.MASTER) {
            throw new IllegalArgumentException("MASTER 역할로 변경할 수 없습니다.");
        }
        this.role = newRole;
    }

    public boolean canManageMembers() {
        return this.role.canManageMembers();
    }

    public boolean canManageGames() {
        return this.role.canManageGames();
    }

    public boolean canManageSurveys() {
        return this.role.canManageSurveys();
    }

    public boolean isMaster() {
        return this.role.isMaster();
    }
}
