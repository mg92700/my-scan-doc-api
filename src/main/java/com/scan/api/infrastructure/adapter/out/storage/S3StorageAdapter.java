package com.scan.api.infrastructure.adapter.out.storage;

import com.scan.api.domain.model.StorageException;
import com.scan.api.domain.port.out.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Adapter sortant — implémente StoragePort via AWS S3 / MinIO.
 * Toute erreur SDK est traduite en {@link StorageException} (exception du domaine).
 * Les retries / timeouts sont configurés sur le client dans S3Config.
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
        LocalDate d = LocalDate.now(ZoneOffset.UTC);
        String ext = (suggestedExt == null || suggestedExt.isBlank()) ? "pdf" : suggestedExt;
        String key = "%d/%02d/%02d/%s.%s".formatted(
                d.getYear(), d.getMonthValue(), d.getDayOfMonth(), UUID.randomUUID(), ext);
        try {
            s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType),
                    RequestBody.fromInputStream(input, size));
            return key;
        } catch (SdkException e) {
            throw new StorageException("Échec écriture stockage key=" + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        GetObjectRequest req = GetObjectRequest.builder().bucket(bucket).key(key).build();
        try (ResponseInputStream<?> is = s3.getObject(req)) {
            return is.readAllBytes();
        } catch (NoSuchKeyException e) {
            throw new StorageException("Objet absent du stockage key=" + key, e);
        } catch (SdkException | IOException e) {
            throw new StorageException("Échec lecture stockage key=" + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw new StorageException("Échec suppression stockage key=" + key, e);
        }
    }
}
