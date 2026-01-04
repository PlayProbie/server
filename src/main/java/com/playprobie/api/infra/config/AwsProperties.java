package com.playprobie.api.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "aws")
@Validated
@Getter
@Setter
public class AwsProperties {

    @NotBlank(message = "AWS region is required")
    private String region;

    @NotBlank(message = "AWS access key is required")
    private String accessKey;

    @NotBlank(message = "AWS secret key is required")
    private String secretKey;

    @Valid
    private S3Properties s3 = new S3Properties();

    @Valid
    private GameLiftProperties gamelift = new GameLiftProperties();

    @Getter
    @Setter
    public static class S3Properties {
        @NotBlank(message = "S3 bucket name is required")
        private String bucketName;
        private String roleArn;
    }

    @Getter
    @Setter
    public static class GameLiftProperties {
        private String roleArn;
    }
}
