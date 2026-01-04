package com.playprobie.api.domain.game.dto;

import java.util.UUID;

public record CreatePresignedUrlResponse(
        UUID buildId,
        String uploadUrl,
        String s3Key,
        Long expiresInSeconds) {
}
