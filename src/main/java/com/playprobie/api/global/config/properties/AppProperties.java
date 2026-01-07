package com.playprobie.api.global.config.properties;

import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "playprobie")
public record AppProperties(
	@NotBlank(message = "Base URL must be defined") @URL(message = "Base URL must be a valid URL")
	String baseUrl) {
}
