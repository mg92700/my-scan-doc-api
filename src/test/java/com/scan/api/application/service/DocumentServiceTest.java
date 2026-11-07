package com.scan.api.application.service;

import com.scan.api.domain.model.ConversionException;
import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentAlreadyExistsException;
import com.scan.api.domain.model.DocumentNotFoundException;
import com.scan.api.domain.model.StorageException;
import com.scan.api.domain.port.in.DocumentUseCase;
import com.scan.api.domain.port.out.DocumentRepositoryPort;
import com.scan.api.domain.port.out.ImageToPdfPort;
import com.scan.api.domain.port.out.StoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentServiceTest {

    private static final byte[] PDF = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    private final DocumentRepositoryPort repository = mock(DocumentRepositoryPort.class);
    private final StoragePort storage = mock(StoragePort.class);
    private final ImageToPdfPort converter = mock(ImageToPdfPort.class);
    private final DocumentService service = new DocumentService(repository, storage, converter);

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    @Test
    void save_stocke_et_persiste_le_document() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findBySha256(anyString())).thenReturn(Optional.empty());
        when(storage.store(any(InputStream.class), eq((long) PDF.length), eq("application/pdf"), eq("pdf")))
                .thenReturn("2026/01/01/x.pdf");
        when(repository.save(any())).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.setId(id);
            return d;
        });

        Document doc = service.save("scan.pdf", "application/pdf", new ByteArrayInputStream(PDF), PDF.length);

        assertThat(doc.getId()).isEqualTo(id);
        assertThat(doc.getS3Key()).isEqualTo("2026/01/01/x.pdf");
        assertThat(doc.getSize()).isEqualTo(PDF.length);
        assertThat(doc.getSha256()).hasSize(64);
    }

    @Test
    void save_retourne_le_document_existant_si_meme_sha256() throws Exception {
        Document existing = Document.builder().id(UUID.randomUUID()).s3Key("k").createdAt(Instant.now()).build();
        when(repository.findBySha256(anyString())).thenReturn(Optional.of(existing));

        Document doc = service.save("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), PDF.length);

        assertThat(doc).isSameAs(existing);
        verifyNoInteractions(storage);
        verify(repository, never()).save(any());
    }

    @Test
    void save_rejette_un_fichier_non_pdf() {
        byte[] txt = "hello world".getBytes();
        assertThatThrownBy(() -> service.save("a.txt", "text/plain", new ByteArrayInputStream(txt), txt.length))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
        verifyNoInteractions(storage);
    }

    @Test
    void save_rejette_une_taille_invalide() {
        assertThatThrownBy(() -> service.save("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), 21L * 1024 * 1024))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void save_rejette_si_taille_reelle_depasse_la_limite_malgre_taille_declaree() {
        byte[] big = new byte[DocumentService.MAX_SIZE + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertThatThrownBy(() -> service.save("a.pdf", "application/pdf", new ByteArrayInputStream(big), 10))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void save_upload_concurrent_supprime_l_objet_et_retourne_le_gagnant() throws Exception {
        Document winner = Document.builder().id(UUID.randomUUID()).s3Key("winner").build();
        when(repository.findBySha256(anyString())).thenReturn(Optional.empty(), Optional.of(winner));
        when(storage.store(any(), anyLong(), any(), any())).thenReturn("loser");
        when(repository.save(any())).thenThrow(new DocumentAlreadyExistsException("sha", null));

        Document doc = service.save("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), PDF.length);

        assertThat(doc).isSameAs(winner);
        verify(storage).delete("loser");
    }

    @Test
    void save_echec_persistance_supprime_l_objet_et_propage() {
        when(repository.findBySha256(anyString())).thenReturn(Optional.empty());
        when(storage.store(any(), anyLong(), any(), any())).thenReturn("k");
        when(repository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.save("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), PDF.length))
                .isInstanceOf(IllegalStateException.class).hasMessage("db down");
        verify(storage).delete("k");
    }

    @Test
    void save_echec_compensation_ne_masque_pas_l_erreur_d_origine() {
        when(repository.findBySha256(anyString())).thenReturn(Optional.empty());
        when(storage.store(any(), anyLong(), any(), any())).thenReturn("k");
        when(repository.save(any())).thenThrow(new IllegalStateException("db down"));
        doThrow(new StorageException("s3 down", null)).when(storage).delete("k");

        assertThatThrownBy(() -> service.save("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), PDF.length))
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

    // ---------------------------------------------------------------- images

    @Test
    void save_pdf_n_est_jamais_converti() throws Exception {
        when(repository.findBySha256(anyString())).thenReturn(Optional.empty());
        when(storage.store(any(), anyLong(), anyString(), anyString())).thenReturn("k");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.save("a.pdf", "application/pdf", new ByteArrayInputStream(PDF), PDF.length);

        verifyNoInteractions(converter);
    }

    @Test
    void save_image_est_convertie_en_pdf() throws Exception {
        byte[] pdfConverti = "%PDF-1.7 converti".getBytes(StandardCharsets.US_ASCII);
        when(repository.findBySha256(anyString())).thenReturn(Optional.empty());
        when(converter.toPdf(JPEG)).thenReturn(pdfConverti);
        when(storage.store(any(InputStream.class), anyLong(), anyString(), anyString())).thenReturn("2026/01/01/x.pdf");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Document doc = service.save("photo.jpg", "image/jpeg", new ByteArrayInputStream(JPEG), JPEG.length);

        assertThat(doc.getContentType()).isEqualTo("application/pdf");
        assertThat(doc.getSize()).isEqualTo(pdfConverti.length);
        assertThat(doc.getSha256()).isEqualTo(sha256(JPEG)); // empreinte du fichier reçu
        assertThat(doc.getOriginalFilename()).isEqualTo("photo.jpg");
        verify(storage).store(any(InputStream.class), eq((long) pdfConverti.length), eq("application/pdf"), eq("pdf"));
    }

    @Test
    void save_image_non_convertible_est_archivee_en_zip_sans_alteration() throws Exception {
        when(repository.findBySha256(anyString())).thenReturn(Optional.empty());
        when(converter.toPdf(any())).thenThrow(new ConversionException("format non pris en charge"));
        when(storage.store(any(InputStream.class), anyLong(), anyString(), anyString())).thenReturn("2026/01/01/x.zip");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<InputStream> flux = ArgumentCaptor.forClass(InputStream.class);

        Document doc = service.save("scan.jpg", "image/jpeg", new ByteArrayInputStream(JPEG), JPEG.length);

        verify(storage).store(flux.capture(), anyLong(), eq("application/zip"), eq("zip"));
        byte[] zip = flux.getValue().readAllBytes();
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entree = archive.getNextEntry();
            assertThat(entree.getName()).isEqualTo("scan.jpg");
            assertThat(archive.readAllBytes()).isEqualTo(JPEG); // octet pour octet
            assertThat(archive.getNextEntry()).isNull();
        }
        assertThat(doc.getContentType()).isEqualTo("application/zip");
        assertThat(doc.getSize()).isEqualTo(zip.length);
        assertThat(doc.getSha256()).isEqualTo(sha256(JPEG));
    }

    @Test
    void save_image_deja_connue_n_est_pas_reconvertie() throws Exception {
        Document existing = Document.builder().id(UUID.randomUUID()).s3Key("k").build();
        when(repository.findBySha256(anyString())).thenReturn(Optional.of(existing));

        Document doc = service.save("photo.jpg", "image/jpeg", new ByteArrayInputStream(JPEG), JPEG.length);

        assertThat(doc).isSameAs(existing);
        verifyNoInteractions(converter, storage);
    }

    @Test
    void save_format_inconnu_refuse_sans_conversion_ni_stockage() {
        byte[] exe = {0x4D, 0x5A, (byte) 0x90, 0, 3, 0, 0, 0};

        assertThatThrownBy(() -> service.save("a.exe", "application/pdf", new ByteArrayInputStream(exe), exe.length))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
        verifyNoInteractions(converter, storage);
    }

    static Stream<Arguments> nomsEntreeZip() {
        return Stream.of(
                Arguments.of("scan.jpg", "scan.jpg"),
                Arguments.of("../../etc/passwd.heic", "passwd.heic"),
                Arguments.of("C:\\Users\\moi\\photo.heic", "photo.heic"),
                Arguments.of("a b.jpg", "a b.jpg"),
                Arguments.of("rapport*?.png", "rapport__.png"),
                Arguments.of("...", "image"),
                Arguments.of("   ", "image"),
                Arguments.of(null, "image"));
    }

    @ParameterizedTest
    @MethodSource("nomsEntreeZip")
    void nom_d_entree_zip_assaini(String nom, String attendu) {
        assertThat(DocumentService.entryName(nom)).isEqualTo(attendu);
    }

    static Stream<Arguments> nomsTelechargement() {
        return Stream.of(
                Arguments.of("photo.jpg", "application/pdf", "photo.pdf"),
                Arguments.of("scan.pdf", "application/pdf", "scan.pdf"),
                Arguments.of("SCAN.PDF", "application/pdf", "SCAN.PDF"),
                Arguments.of("sans-extension", "application/pdf", "sans-extension.pdf"),
                Arguments.of("photo.heic", "application/zip", "photo.heic.zip"),
                Arguments.of(null, "application/pdf", "document.pdf"),
                Arguments.of("  ", "application/zip", "document.zip"),
                Arguments.of("autre.bin", "application/octet-stream", "autre.bin"));
    }

    @ParameterizedTest
    @MethodSource("nomsTelechargement")
    void nom_propose_au_telechargement_reflete_le_contenu_stocke(String nom, String type, String attendu) {
        assertThat(DocumentService.downloadName(nom, type)).isEqualTo(attendu);
    }

    @Test
    void download_image_convertie_porte_l_extension_pdf() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(
                Document.builder().id(id).originalFilename("photo.jpg").contentType("application/pdf").s3Key("k").build()));
        when(storage.get("k")).thenReturn(PDF);

        DocumentUseCase.DownloadResult res = service.download(id);

        assertThat(res.filename()).isEqualTo("photo.pdf");
        assertThat(res.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void download_archive_zip_porte_l_extension_zip() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(
                Document.builder().id(id).originalFilename("photo.heic").contentType("application/zip").s3Key("k").build()));
        when(storage.get("k")).thenReturn(new byte[]{'P', 'K'});

        DocumentUseCase.DownloadResult res = service.download(id);

        assertThat(res.filename()).isEqualTo("photo.heic.zip");
        assertThat(res.contentType()).isEqualTo("application/zip");
    }
}
