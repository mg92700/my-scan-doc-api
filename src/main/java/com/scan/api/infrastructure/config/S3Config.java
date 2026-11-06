package com.scan.api.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

/**
 * Configuration S3/MinIO — inchangée fonctionnellement, déplacée dans infrastructure/config/.
 */
@Configuration
public class S3Config {

    /**
     * Timeouts bornés + retries "standard" (backoff exponentiel avec jitter) :
     * un S3 lent ou indisponible ne bloque pas indéfiniment les threads HTTP.
     */
    @Bean
    public S3Client s3Client(
            @Value("${app.s3.endpoint}") String endpoint,
            @Value("${app.s3.region}") String region,
            @Value("${app.s3.access-key}") String accessKey,
            @Value("${app.s3.secret-key}") String secretKey,
            @Value("${app.s3.path-style-access:true}") boolean pathStyle,
            @Value("${app.s3.api-call-timeout:30s}") Duration apiCallTimeout,
            @Value("${app.s3.api-call-attempt-timeout:10s}") Duration attemptTimeout
    ) {
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .forcePathStyle(pathStyle)
                .overrideConfiguration(c -> c
                        .retryStrategy(RetryMode.STANDARD)
                        .apiCallTimeout(apiCallTimeout)
                        .apiCallAttemptTimeout(attemptTimeout))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(
            @Value("${app.s3.endpoint}") String endpoint,
            @Value("${app.s3.region}") String region,
            @Value("${app.s3.access-key}") String accessKey,
            @Value("${app.s3.secret-key}") String secretKey
    ) {
        return S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .build();
    }
}
