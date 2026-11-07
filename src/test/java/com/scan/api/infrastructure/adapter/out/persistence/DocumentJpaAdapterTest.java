package com.scan.api.infrastructure.adapter.out.persistence;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentJpaAdapterTest {

    private static final Instant MAINTENANT = Instant.parse("2026-01-01T00:00:00Z");

    private final DocumentJpaRepository repo = mock(DocumentJpaRepository.class);
    private final DocumentJpaAdapter adapter = new DocumentJpaAdapter(repo);

    private static Document document(UUID id, String sha) {
        return Document.builder().id(id).originalFilename("a.pdf").size(3)
                .contentType("application/pdf").sha256(sha).s3Key("k").createdAt(MAINTENANT).build();
    }

    private static DocumentJpaEntity entite(UUID id, String sha) {
        return DocumentJpaEntity.builder().id(id).originalFilename("a.pdf").size(3)
                .contentType("application/pdf").sha256(sha).s3Key("k").createdAt(MAINTENANT).build();
    }

    @Test
    void save_persiste_et_retourne_le_domaine() {
        UUID id = UUID.randomUUID();
        when(repo.saveAndFlush(any(DocumentJpaEntity.class))).thenReturn(entite(id, "sha"));

        Document res = adapter.save(document(id, "sha"));

        assertThat(res.getId()).isEqualTo(id);
        assertThat(res.getSha256()).isEqualTo("sha");
        assertThat(res.getS3Key()).isEqualTo("k");
        assertThat(res.getCreatedAt()).isEqualTo(MAINTENANT);
    }

    @Test
    void save_doublon_leve_document_already_exists() {
        UUID id = UUID.randomUUID();
        when(repo.saveAndFlush(any(DocumentJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("unique"));
        when(repo.findBySha256("sha")).thenReturn(Optional.of(entite(id, "sha")));

        assertThatThrownBy(() -> adapter.save(document(id, "sha")))
                .isInstanceOf(DocumentAlreadyExistsException.class);
    }

    @Test
    void save_violation_sans_doublon_relance_l_erreur() {
        UUID id = UUID.randomUUID();
        when(repo.saveAndFlush(any(DocumentJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("autre contrainte"));
        when(repo.findBySha256("sha")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.save(document(id, "sha")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_sans_sha256_ne_cherche_pas_de_doublon() {
        UUID id = UUID.randomUUID();
        when(repo.saveAndFlush(any(DocumentJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("contrainte"));

        assertThatThrownBy(() -> adapter.save(document(id, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(repo, never()).findBySha256(any());
    }

    @Test
    void find_by_id_present_et_absent() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(entite(id, "sha")));
        when(repo.findById(new UUID(0L, 0L))).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isPresent();
        assertThat(adapter.findById(new UUID(0L, 0L))).isEmpty();
    }

    @Test
    void find_by_sha256_present_et_absent() {
        UUID id = UUID.randomUUID();
        when(repo.findBySha256("sha")).thenReturn(Optional.of(entite(id, "sha")));
        when(repo.findBySha256("autre")).thenReturn(Optional.empty());

        assertThat(adapter.findBySha256("sha")).isPresent();
        assertThat(adapter.findBySha256("autre")).isEmpty();
    }
}
