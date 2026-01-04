package com.playprobie.api.domain.game.dto;

import java.util.UUID;

public record CreateGameBuildResponse(
        UUID buildId,
        String version,
        String s3Prefix,
        AwsCredentials credentials) {
    public record AwsCredentials(
            String accessKeyId,
            String secretAccessKey,
            String sessionToken,
            Long expiration) {
    }
}
