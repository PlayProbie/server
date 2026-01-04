package com.playprobie.api.domain.game.domain;

public enum BuildStatus {
    PENDING, // Presigned URL 발급 후 업로드 대기
    UPLOADED // 업로드 완료 확인됨
}
