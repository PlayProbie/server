package com.playprobie.api.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
	@NotBlank(message = "JWT Secret must be defined") @Size(min = 32, message = "JWT Secret must be at least 32 characters long for security")
	String secret,

	@Positive(message = "Access token expiration must be positive")
	long accessTokenExpiration) {
}
