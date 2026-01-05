package com.playprobie.api.infra.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;

@Builder
public record SessionEmbeddingResponse(
        @JsonProperty("embedding_id") String embeddingId,
        @JsonProperty("success") boolean success,
        @JsonProperty("message") String message) {
}
