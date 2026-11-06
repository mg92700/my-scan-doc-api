package com.scan.api.infrastructure.adapter.out.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Expose l'état du bucket dans /actuator/health (composant "s3").
 * Bucket inaccessible → application DOWN.
 */
@Component("s3")
public class S3HealthIndicator extends AbstractHealthIndicator {

    private final S3Client s3;
    private final String bucket;

    public S3HealthIndicator(S3Client s3, @Value("${app.s3.bucket}") String bucket) {
        super("S3 health check failed");
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        s3.headBucket(b -> b.bucket(bucket));
        builder.up().withDetail("bucket", bucket);
    }
}
