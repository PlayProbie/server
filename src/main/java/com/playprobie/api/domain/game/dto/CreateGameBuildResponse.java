package com.playprobie.api.domain.game.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "게임 빌드 생성 응답 DTO")
public record CreateGameBuildResponse(

                @Schema(description = "생성된 빌드 UUID", example = "550e8400-e29b-41d4-a716-446655440000") @JsonProperty("build_id") UUID buildId,

                @Schema(description = "빌드 버전", example = "1.0.0") @JsonProperty("version") String version,

                @Schema(description = "S3 업로드 경로 프리픽스", example = "builds/550e8400-e29b-41d4-a716-446655440000/") @JsonProperty("s3_prefix") String s3Prefix,

                @Schema(description = "S3 업로드용 임시 AWS 자격증명") @JsonProperty("credentials") AwsCredentials credentials) {

        @Schema(description = "AWS 임시 자격증명")
        public record AwsCredentials(

                        @Schema(description = "AWS Access Key ID", example = "AKIAIOSFODNN7EXAMPLE") @JsonProperty("access_key_id") String accessKeyId,

                        @Schema(description = "AWS Secret Access Key") @JsonProperty("secret_access_key") String secretAccessKey,

                        @Schema(description = "AWS Session Token") @JsonProperty("session_token") String sessionToken,

                        @Schema(description = "자격증명 만료 시간 (Unix timestamp)", example = "1704067200") @JsonProperty("expiration") Long expiration) {
        }
}
