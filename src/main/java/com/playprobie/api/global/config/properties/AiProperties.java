package com.playprobie.api.global.config.properties;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
	@Valid @NotNull
	Server server,

	@Valid @NotNull
	Client client,

	@Valid @NotNull
	Interview interview,

	@Valid @NotNull
	Sse sse) {
	public record Server(
		@NotBlank(message = "AI Server URL must be defined") @URL(message = "AI Server URL must be a valid URL")
		String url) {
	}

	public record Client(
		@DurationUnit(ChronoUnit.MILLIS) @NotNull
		Duration connectTimeout,

		@DurationUnit(ChronoUnit.MILLIS) @NotNull
		Duration readTimeout) {
	}

	public record Interview(
		@Positive @Max(value = 5, message = "Max tail questions cannot exceed 5")
		int maxTailQuestions) {
	}

	public record Sse(
		@DurationUnit(ChronoUnit.MILLIS) @NotNull
		Duration timeout) {
	}
}
