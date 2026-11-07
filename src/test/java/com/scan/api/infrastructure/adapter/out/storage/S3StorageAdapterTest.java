package com.scan.api.infrastructure.adapter.out.storage;

import com.scan.api.domain.model.StorageException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3StorageAdapterTest {

    private final S3Client s3 = mock(S3Client.class);
    private final S3StorageAdapter adapter = new S3StorageAdapter(s3, "bucket");

    private static SdkException erreurSdk() {
        return SdkException.builder().message("boom").build();
    }

    private static ResponseInputStream<GetObjectResponse> corps(InputStream in) {
        return new ResponseInputStream<>(GetObjectResponse.builder().build(), AbortableInputStream.create(in));
    }

    @Test
    void store_retourne_une_cle_datee_avec_uuid_et_extension() {
        when(s3.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = adapter.store(new ByteArrayInputStream(new byte[]{1, 2}), 2, "application/pdf", "pdf");

        assertThat(key).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.pdf");
    }

    @Test
    void store_extension_par_defaut_si_absente() {
        when(s3.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = adapter.store(new ByteArrayInputStream(new byte[]{1}), 1, "application/pdf", "  ");

        assertThat(key).endsWith(".pdf");
    }

    @Test
    void store_erreur_sdk_devient_storage_exception() {
        when(s3.putObject(any(Consumer.class), any(RequestBody.class))).thenThrow(erreurSdk());

        assertThatThrownBy(() -> adapter.store(new ByteArrayInputStream(new byte[]{1}), 1, "application/pdf", "pdf"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Échec écriture");
    }

    @Test
    void get_retourne_les_octets() {
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(corps(new ByteArrayInputStream("abc".getBytes())));

        assertThat(adapter.get("k")).isEqualTo("abc".getBytes());
    }

    @Test
    void get_cle_absente_devient_storage_exception() {
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("absent").build());

        assertThatThrownBy(() -> adapter.get("k"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Objet absent");
    }

    @Test
    void get_erreur_sdk_devient_storage_exception() {
        when(s3.getObject(any(GetObjectRequest.class))).thenThrow(erreurSdk());

        assertThatThrownBy(() -> adapter.get("k"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Échec lecture");
    }

    @Test
    void get_erreur_de_lecture_devient_storage_exception() {
        InputStream casse = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("coupure réseau");
            }
        };
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(corps(casse));

        assertThatThrownBy(() -> adapter.get("k"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Échec lecture");
    }

    @Test
    void delete_appelle_s3() {
        when(s3.deleteObject(any(Consumer.class))).thenReturn(DeleteObjectResponse.builder().build());

        adapter.delete("k");

        verify(s3).deleteObject(any(Consumer.class));
    }

    @Test
    void delete_erreur_sdk_devient_storage_exception() {
        when(s3.deleteObject(any(Consumer.class))).thenThrow(erreurSdk());

        assertThatThrownBy(() -> adapter.delete("k"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Échec suppression");
    }
}
