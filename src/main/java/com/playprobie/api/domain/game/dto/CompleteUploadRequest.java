package com.playprobie.api.domain.game.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CompleteUploadRequest(
		@NotNull(message = "예상 파일 수는 필수입니다.") @Min(value = 1, message = "최소 1개 이상의 파일이 필요합니다.") Integer expectedFileCount,

		@NotNull(message = "예상 총 크기는 필수입니다.") @Min(value = 1, message = "총 크기는 1바이트 이상이어야 합니다.") Long expectedTotalSize,

		@NotBlank(message = "실행 파일 경로는 필수입니다.") String executablePath,

		@NotBlank(message = "OS 타입은 필수입니다.") @Pattern(regexp = "WINDOWS|LINUX", message = "OS 타입은 WINDOWS 또는 LINUX여야 합니다.") String osType) {
}
