package com.playprobie.api.domain.game.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateGameBuildResponse(
                @JsonProperty("build_id") UUID buildId,
                @JsonProperty("version") String version,
                @JsonProperty("s3_prefix") String s3Prefix,
                @JsonProperty("credentials") AwsCredentials credentials) {
        public record AwsCredentials(
                        @JsonProperty("access_key_id") String accessKeyId,
                        @JsonProperty("secret_access_key") String secretAccessKey,
                        @JsonProperty("session_token") String sessionToken,
                        @JsonProperty("expiration") Long expiration) {
        }
}
