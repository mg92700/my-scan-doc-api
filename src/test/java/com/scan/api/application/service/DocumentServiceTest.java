package com.scan.api.application.service;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentCreatedEvent;
import com.scan.api.domain.model.DocumentNotFoundException;
import com.scan.api.domain.port.in.DocumentUseCase;
import com.scan.api.domain.port.out.DocumentRepositoryPort;
import com.scan.api.domain.port.out.OutboxPort;
import com.scan.api.domain.port.out.StoragePort;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentServiceTest {

    private static final byte[] PDF = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    private final DocumentRepositoryPort repository = mock(DocumentRepositoryPort.class);
    private final StoragePort storage = mock(StoragePort.class);
    private final OutboxPort outbox = mock(OutboxPort.class);
    private final DocumentService service = new DocumentService(repository, storage, outbox);

    @Test
    void savePdf_stocke_persiste_et_enqueue_l_evenement() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findBySha256(anyString())).thenReturn(Optional.empty());
        when(storage.store(any(InputStream.class), eq((long) PDF.length), eq("application/pdf"), eq("pdf")))
                .thenReturn("2026/01/01/x.pdf");
        when(repository.save(any())).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.setId(id);
            return d;
        });

        Document doc = service.savePdf("scan.pdf", "application/pdf", new ByteArrayInputStream(PDF), PDF.length);

        assertThat(doc.getId()).isEqualTo(id);
        assertThat(doc.getS3Key()).isEqualTo("2026/01/01/x.pdf");
        assertThat(doc.getSize()).isEqualTo(PDF.length);
        assertThat(doc.getSha256()).hasSize(64);
        verify(outbox).enqueue(eq(id), eq("DocumentCreated"),
                eq(new DocumentCreatedEvent(id, "scan.pdf", "2026/01/01/x.pdf", "application/pdf", PDF.length)));
    }

    @Test
    void savePdf_retourne_le_document_existant_si_meme_sha256() throws Exception {
        Document existing = Document.builder().id(UUID.randomUUID()).s3Key("k").createdAt(Instant.now()).build();
        when(repository.findBySha256(anyString())).thenReturn(Optional.of(existing));

        Document doc = service.savePdf("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), PDF.length);

        assertThat(doc).isSameAs(existing);
        verifyNoInteractions(storage, outbox);
        verify(repository, never()).save(any());
    }

    @Test
    void savePdf_rejette_un_fichier_non_pdf() {
        byte[] txt = "hello world".getBytes();
        assertThatThrownBy(() -> service.savePdf("a.txt", "text/plain", new ByteArrayInputStream(txt), txt.length))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
        verifyNoInteractions(storage, outbox);
    }

    @Test
    void savePdf_rejette_une_taille_invalide() {
        assertThatThrownBy(() -> service.savePdf("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.savePdf("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), 21L * 1024 * 1024))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void download_retourne_les_bytes() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(
                Document.builder().id(id).originalFilename("a.pdf").contentType("application/pdf").s3Key("k").build()));
        when(storage.get("k")).thenReturn(PDF);

        DocumentUseCase.DownloadResult res = service.download(id);

        assertThat(res.filename()).isEqualTo("a.pdf");
        assertThat(res.bytes()).isEqualTo(PDF);
    }

    @Test
    void download_document_inconnu() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.download(id)).isInstanceOf(DocumentNotFoundException.class);
    }
}
