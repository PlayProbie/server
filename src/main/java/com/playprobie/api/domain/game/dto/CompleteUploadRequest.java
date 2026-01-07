package com.playprobie.api.domain.game.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "빌드 업로드 완료 요청 DTO")
public record CompleteUploadRequest(

	@Schema(description = "업로드된 총 파일 수 (최소 1개)", example = "42", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "예상 파일 수는 필수입니다.") @Min(value = 1, message = "최소 1개 이상의 파일이 필요합니다.") @JsonProperty("expected_file_count")
	Integer expectedFileCount,

	@Schema(description = "업로드된 총 파일 크기 (바이트, 최소 1바이트)", example = "1073741824", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "예상 총 크기는 필수입니다.") @Min(value = 1, message = "총 크기는 1바이트 이상이어야 합니다.") @JsonProperty("expected_total_size")
	Long expectedTotalSize,

	@Schema(description = "게임 실행 파일 경로", example = "MyGame/Binaries/Win64/MyGame.exe", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "실행 파일 경로는 필수입니다.") @JsonProperty("executable_path")
	String executablePath,

	@Schema(description = "운영체제 타입 (WINDOWS, LINUX)", example = "WINDOWS", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "OS 타입은 필수입니다.") @Pattern(regexp = "WINDOWS|LINUX", message = "OS 타입은 WINDOWS 또는 LINUX여야 합니다.") @JsonProperty("os_type")
	String osType) {
}
