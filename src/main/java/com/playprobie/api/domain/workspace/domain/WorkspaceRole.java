package com.playprobie.api.domain.workspace.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorkspaceRole {
    OWNER("소유자"),
    ADMIN("관리자"),
    MEMBER("일반 멤버"),
    VIEWER("뷰어");

    private final String description;
}
