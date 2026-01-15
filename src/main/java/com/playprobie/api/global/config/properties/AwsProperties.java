package com.playprobie.api.global.config.properties;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
	@NotBlank(message = "AWS Region must be defined (e.g. aws.region=ap-northeast-2)")
	String region,

	@NotBlank(message = "AWS Access Key must be defined (e.g. aws.access-key=AKI...)")
	String accessKey,

	@NotBlank(message = "AWS Secret Key must be defined (e.g. aws.secret-key=...)")
	String secretKey,

	@Valid @NotNull
	S3 s3,

	@Valid @NotNull
	GameLift gamelift) {
	public record S3(
		@NotBlank(message = "S3 Region must be defined (e.g. aws.s3.region=ap-northeast-1)")
		String region,

		@NotBlank(message = "S3 Bucket Name must be defined (e.g. aws.s3.bucket-name=...)")
		String bucketName,

		@DurationUnit(ChronoUnit.SECONDS) @NotNull
		Duration credentialsDuration,

		@NotBlank(message = "S3 Role ARN must be defined")
		String roleArn) {
	}

	public record GameLift(
		@NotBlank(message = "GameLift Region must be defined")
		String region,

		@DurationUnit(ChronoUnit.SECONDS) @NotNull
		Duration provisioningTimeout,

		@DurationUnit(ChronoUnit.MILLIS) @NotNull
		Duration pollingInterval,

		@NotBlank(message = "GameLift Role ARN must be defined")
		String roleArn) {
	}
}
