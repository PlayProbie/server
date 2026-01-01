package com.playprobie.api.domain.workspace.domain;

/**
 * 워크스페이스 내 역할
 */
public enum WorkspaceRole {
    MASTER, // 마스터 관리자 (1명, Workspace 생성자)
    ADMIN, // 관리자 (멤버 초대/삭제, Game 관리)
    PLANNER, // 기획자 (Survey 생성/관리)
    DEVELOPER, // 개발자 (읽기 권한)
    VIEWER; // 뷰어 (읽기 전용)

    public boolean canManageMembers() {
        return this == MASTER || this == ADMIN;
    }

    public boolean canManageGames() {
        return this == MASTER || this == ADMIN;
    }

    public boolean canManageSurveys() {
        return this == MASTER || this == ADMIN || this == PLANNER;
    }

    public boolean isMaster() {
        return this == MASTER;
    }
}
