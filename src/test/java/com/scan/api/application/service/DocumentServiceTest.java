package com.scan.api.application.service;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentAlreadyExistsException;
import com.scan.api.domain.model.DocumentNotFoundException;
import com.scan.api.domain.model.StorageException;
import com.scan.api.domain.port.in.DocumentUseCase;
import com.scan.api.domain.port.out.DocumentRepositoryPort;
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
    private final DocumentService service = new DocumentService(repository, storage);

    @Test
    void savePdf_stocke_et_persiste_le_document() throws Exception {
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
    }

    @Test
    void savePdf_retourne_le_document_existant_si_meme_sha256() throws Exception {
        Document existing = Document.builder().id(UUID.randomUUID()).s3Key("k").createdAt(Instant.now()).build();
        when(repository.findBySha256(anyString())).thenReturn(Optional.of(existing));

        Document doc = service.savePdf("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), PDF.length);

        assertThat(doc).isSameAs(existing);
        verifyNoInteractions(storage);
        verify(repository, never()).save(any());
    }

    @Test
    void savePdf_rejette_un_fichier_non_pdf() {
        byte[] txt = "hello world".getBytes();
        assertThatThrownBy(() -> service.savePdf("a.txt", "text/plain", new ByteArrayInputStream(txt), txt.length))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
        verifyNoInteractions(storage);
    }

    @Test
    void savePdf_rejette_une_taille_invalide() {
        assertThatThrownBy(() -> service.savePdf("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.savePdf("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), 21L * 1024 * 1024))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void savePdf_rejette_si_taille_reelle_depasse_la_limite_malgre_taille_declaree() {
        byte[] big = new byte[DocumentService.MAX_SIZE + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertThatThrownBy(() -> service.savePdf("a.pdf", "application/pdf", new ByteArrayInputStream(big), 10))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void savePdf_upload_concurrent_supprime_l_objet_et_retourne_le_gagnant() throws Exception {
        Document winner = Document.builder().id(UUID.randomUUID()).s3Key("winner").build();
        when(repository.findBySha256(anyString())).thenReturn(Optional.empty(), Optional.of(winner));
        when(storage.store(any(), anyLong(), any(), any())).thenReturn("loser");
        when(repository.save(any())).thenThrow(new DocumentAlreadyExistsException("sha", null));

        Document doc = service.savePdf("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), PDF.length);

        assertThat(doc).isSameAs(winner);
        verify(storage).delete("loser");
    }

    @Test
    void savePdf_echec_persistance_supprime_l_objet_et_propage() {
        when(repository.findBySha256(anyString())).thenReturn(Optional.empty());
        when(storage.store(any(), anyLong(), any(), any())).thenReturn("k");
        when(repository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.savePdf("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), PDF.length))
                .isInstanceOf(IllegalStateException.class).hasMessage("db down");
        verify(storage).delete("k");
    }

    @Test
    void savePdf_echec_compensation_ne_masque_pas_l_erreur_d_origine() {
        when(repository.findBySha256(anyString())).thenReturn(Optional.empty());
        when(storage.store(any(), anyLong(), any(), any())).thenReturn("k");
        when(repository.save(any())).thenThrow(new IllegalStateException("db down"));
        doThrow(new StorageException("s3 down", null)).when(storage).delete("k");

        assertThatThrownBy(() -> service.savePdf("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), PDF.length))
                .isInstanceOf(IllegalStateException.class).hasMessage("db down");
    }

    @Test
    void sanitizeFilename() {
        assertThat(DocumentService.sanitizeFilename(null)).isEqualTo("document.pdf");
        assertThat(DocumentService.sanitizeFilename("   ")).isEqualTo("document.pdf");
        assertThat(DocumentService.sanitizeFilename("C:\\Users\\x\\scan.pdf")).isEqualTo("scan.pdf");
        assertThat(DocumentService.sanitizeFilename("../../etc/a.pdf")).isEqualTo("a.pdf");
        assertThat(DocumentService.sanitizeFilename("a\r\nb.pdf")).isEqualTo("ab.pdf");
        String longName = DocumentService.sanitizeFilename("x".repeat(400) + ".pdf");
        assertThat(longName).hasSize(255).endsWith(".pdf");
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
