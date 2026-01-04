package com.playprobie.api.domain.game.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteUploadRequest(
	@NotBlank(message = "S3 Key는 필수입니다.") String s3Key) {
}
