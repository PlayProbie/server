package com.playprobie.api.global.config.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;

@Validated
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
	@NotEmpty(message = "Allowed origins must not be empty")
	List<String> allowedOrigins) {
}
