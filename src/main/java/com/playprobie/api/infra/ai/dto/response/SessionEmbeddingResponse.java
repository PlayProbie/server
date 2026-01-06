package com.playprobie.api.infra.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "세션 임베딩 응답 DTO")
@Builder
public record SessionEmbeddingResponse(

                @Schema(description = "임베딩 ID", example = "emb_12345") @JsonProperty("embedding_id") String embeddingId,

                @Schema(description = "성공 여부", example = "true") @JsonProperty("success") boolean success,

                @Schema(description = "응답 메시지", example = "Successfully processed") @JsonProperty("message") String message) {
}
