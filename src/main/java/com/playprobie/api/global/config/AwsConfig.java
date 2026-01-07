package com.playprobie.api.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.playprobie.api.global.config.properties.AwsProperties;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.gamelift.GameLiftClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sts.StsClient;

@Configuration
@RequiredArgsConstructor
public class AwsConfig {

	private final AwsProperties awsProperties;

	@Bean
	public AwsCredentialsProvider awsCredentialsProvider() {
		return StaticCredentialsProvider.create(
			AwsBasicCredentials.create(
				awsProperties.accessKey(),
				awsProperties.secretKey()));
	}

	@Bean
	public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
		return S3Client.builder()
			.region(Region.of(awsProperties.s3().region()))
			.credentialsProvider(credentialsProvider)
			.build();
	}

	@Bean(destroyMethod = "close")
	public S3Presigner s3Presigner(AwsCredentialsProvider credentialsProvider) {
		return S3Presigner.builder()
			.region(Region.of(awsProperties.s3().region()))
			.credentialsProvider(credentialsProvider)
			.build();
	}

	@Bean
	public GameLiftClient gameLiftClient(AwsCredentialsProvider credentialsProvider) {
		return GameLiftClient.builder()
			.region(Region.of(awsProperties.region()))
			.credentialsProvider(credentialsProvider)
			.build();
	}

	@Bean
	public StsClient stsClient(AwsCredentialsProvider credentialsProvider) {
		return StsClient.builder()
			.region(Region.of(awsProperties.region()))
			.credentialsProvider(credentialsProvider)
			.build();
	}

	@Bean
	public software.amazon.awssdk.services.gameliftstreams.GameLiftStreamsClient gameLiftStreamsClient(
		AwsCredentialsProvider credentialsProvider) {
		return software.amazon.awssdk.services.gameliftstreams.GameLiftStreamsClient.builder()
			.region(Region.of(awsProperties.gamelift().region()))
			.credentialsProvider(credentialsProvider)
			.build();
	}
}
