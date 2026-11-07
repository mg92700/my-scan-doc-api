package com.scan.api.infrastructure.adapter.out.persistence;

import com.scan.api.domain.model.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentJpaAdapterManagementTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final DocumentJpaRepository repo = mock(DocumentJpaRepository.class);
    private final DocumentJpaAdapter adapter = new DocumentJpaAdapter(repo);

    private static DocumentJpaEntity entite(UUID id, Instant archivedAt) {
        return DocumentJpaEntity.builder().id(id).originalFilename("a.pdf").size(3)
                .contentType("application/pdf").sha256("sha").s3Key("k").createdAt(T0)
                .archivedAt(archivedAt).build();
    }

    @Test
    void findAllByArchived_false_lit_les_actifs() {
        UUID id = UUID.randomUUID();
        when(repo.findByArchivedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(entite(id, null)));

        List<Document> res = adapter.findAllByArchived(false);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).getId()).isEqualTo(id);
        assertThat(res.get(0).getArchivedAt()).isNull();
        verify(repo, never()).findByArchivedAtIsNotNullOrderByCreatedAtDesc();
    }

    @Test
    void findAllByArchived_true_lit_les_archives() {
        when(repo.findByArchivedAtIsNotNullOrderByCreatedAtDesc())
                .thenReturn(List.of(entite(UUID.randomUUID(), T0)));

        assertThat(adapter.findAllByArchived(true).get(0).getArchivedAt()).isEqualTo(T0);
    }

    @Test
    void save_conserve_la_date_d_archivage() {
        UUID id = UUID.randomUUID();
        when(repo.saveAndFlush(any(DocumentJpaEntity.class))).thenAnswer(i -> i.getArgument(0));
        Document d = Document.builder().id(id).originalFilename("a.pdf").size(3).contentType("application/pdf")
                .sha256("sha").s3Key("k").createdAt(T0).archivedAt(T0).build();

        assertThat(adapter.save(d).getArchivedAt()).isEqualTo(T0);
    }

    @Test
    void deleteById_delegue() {
        UUID id = UUID.randomUUID();

        adapter.deleteById(id);

        verify(repo).deleteById(id);
    }
}
