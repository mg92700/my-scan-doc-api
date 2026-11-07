package com.scan.api.application.service;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentNotFoundException;
import com.scan.api.domain.model.StorageException;
import com.scan.api.domain.port.out.DocumentRepositoryPort;
import com.scan.api.domain.port.out.ImageToPdfPort;
import com.scan.api.domain.port.out.StoragePort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Consultation, archivage et suppression (cas d'usage de gestion). */
class DocumentServiceManagementTest {

    private final DocumentRepositoryPort repository = mock(DocumentRepositoryPort.class);
    private final StoragePort storage = mock(StoragePort.class);
    private final DocumentService service =
            new DocumentService(repository, storage, mock(ImageToPdfPort.class));

    private static Document doc(UUID id, Instant archivedAt) {
        return Document.builder().id(id).originalFilename("a.pdf").size(3).contentType("application/pdf")
                .sha256("sha").s3Key("k.pdf").createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .archivedAt(archivedAt).build();
    }

    @Test
    void list_delegue_au_repository() {
        List<Document> actifs = List.of(doc(UUID.randomUUID(), null));
        when(repository.findAllByArchived(false)).thenReturn(actifs);

        assertThat(service.list(false)).isSameAs(actifs);
    }

    @Test
    void archive_positionne_la_date_et_sauvegarde() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(doc(id, null)));
        when(repository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        Document res = service.archive(id);

        assertThat(res.getArchivedAt()).isNotNull();
        verify(repository).save(res);
    }

    @Test
    void archive_est_idempotent() {
        UUID id = UUID.randomUUID();
        Instant deja = Instant.parse("2026-02-01T00:00:00Z");
        when(repository.findById(id)).thenReturn(Optional.of(doc(id, deja)));

        assertThat(service.archive(id).getArchivedAt()).isEqualTo(deja);
        verify(repository, never()).save(any());
    }

    @Test
    void unarchive_efface_la_date() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(doc(id, Instant.now())));
        when(repository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.unarchive(id).getArchivedAt()).isNull();
    }

    @Test
    void unarchive_est_idempotent() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(doc(id, null)));

        service.unarchive(id);

        verify(repository, never()).save(any());
    }

    @Test
    void archive_et_unarchive_document_inconnu() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(id)).isInstanceOf(DocumentNotFoundException.class);
        assertThatThrownBy(() -> service.unarchive(id)).isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void delete_supprime_la_base_puis_le_fichier() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(doc(id, null)));

        service.delete(id);

        var ordre = inOrder(repository, storage);
        ordre.verify(repository).deleteById(id);
        ordre.verify(storage).delete("k.pdf");
    }

    @Test
    void delete_reussit_meme_si_le_stockage_echoue() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(doc(id, null)));
        doThrow(new StorageException("indisponible", new RuntimeException())).when(storage).delete("k.pdf");

        service.delete(id);

        verify(repository).deleteById(id);
    }

    @Test
    void delete_document_inconnu() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(DocumentNotFoundException.class);
        verify(repository, never()).deleteById(any());
        verifyNoInteractions(storage);
    }
}
