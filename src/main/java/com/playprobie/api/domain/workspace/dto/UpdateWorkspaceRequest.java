package com.playprobie.api.domain.workspace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "워크스페이스 수정 요청 DTO")
public record UpdateWorkspaceRequest(

	@Schema(description = "워크스페이스 이름 (최대 100자)", example = "Updated Studio Name", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "워크스페이스 이름은 필수입니다") @Size(max = 100, message = "워크스페이스 이름은 100자 이하입니다") @JsonProperty("name")
	String name,

	@Schema(description = "프로필 이미지 URL (최대 512자, 선택 사항)", example = "https://example.com/image.png") @Size(max = 512, message = "프로필 이미지 URL은 512자 이하입니다") @JsonProperty("profile_image_url")
	String profileImageUrl,

	@Schema(description = "워크스페이스 설명 (최대 500자, 선택 사항)", example = "수정된 스튜디오 설명") @Size(max = 500, message = "설명은 500자 이하입니다") @JsonProperty("description")
	String description) {
}
