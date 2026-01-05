package com.playprobie.api.domain.user.domain;

/**
 * 사용자 계정 상태
 */
public enum UserStatus {
    ACTIVE, // 활성
    INACTIVE, // 비활성 (이메일 미인증 등)
    SUSPENDED; // 정지

    public boolean isActive() {
        return this == ACTIVE;
    }
}
