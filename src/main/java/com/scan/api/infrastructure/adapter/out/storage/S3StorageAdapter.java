package com.scan.api.infrastructure.adapter.out.storage;

import com.scan.api.domain.port.out.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter sortant — implémente StoragePort via AWS S3 / MinIO.
 */
@Component
public class S3StorageAdapter implements StoragePort {

    private final S3Client s3;
    private final String bucket;

    public S3StorageAdapter(S3Client s3, @Value("${app.s3.bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public String store(InputStream input, long size, String contentType, String suggestedExt) {
        LocalDate d = LocalDate.now();
        String ext = (suggestedExt == null || suggestedExt.isBlank()) ? "pdf" : suggestedExt;
        String key = "%d/%02d/%02d/%s.%s".formatted(
                d.getYear(), d.getMonthValue(), d.getDayOfMonth(), UUID.randomUUID(), ext);

        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType),
                RequestBody.fromInputStream(input, size));
        return key;
    }

    @Override
    public byte[] get(String key) {
        GetObjectRequest req = GetObjectRequest.builder().bucket(bucket).key(key).build();
        try (ResponseInputStream<?> is = s3.getObject(req)) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Erreur lecture S3 key=" + key, e);
        }
    }
}
