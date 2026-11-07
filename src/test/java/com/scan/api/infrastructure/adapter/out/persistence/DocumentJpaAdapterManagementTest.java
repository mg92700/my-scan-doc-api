package com.scan.api.infrastructure.adapter.out.persistence;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentPage;
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
    void search_transmet_le_motif_l_etat_et_la_page() {
        UUID id = UUID.randomUUID();
        when(repo.search(false, "%facture%", 20, 10))
                .thenReturn(new DocumentSearchFragment.SearchResult(List.of(entite(id, null)), 31));

        DocumentPage res = adapter.search(false, "Facture", 20, 10);

        assertThat(res.total()).isEqualTo(31);
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).getId()).isEqualTo(id);
        assertThat(res.items().get(0).getArchivedAt()).isNull();
    }

    @Test
    void search_des_archives_conserve_la_date() {
        when(repo.search(true, "%%", 0, 20))
                .thenReturn(new DocumentSearchFragment.SearchResult(List.of(entite(UUID.randomUUID(), T0)), 1));

        assertThat(adapter.search(true, "", 0, 20).items().get(0).getArchivedAt()).isEqualTo(T0);
    }

    @Test
    void le_motif_neutralise_les_jokers_et_ignore_la_casse() {
        assertThat(DocumentJpaAdapter.likePattern("")).isEqualTo("%%");
        assertThat(DocumentJpaAdapter.likePattern("ReÇu")).isEqualTo("%reçu%");
        assertThat(DocumentJpaAdapter.likePattern("100%_ok!")).isEqualTo("%100!%!_ok!!%");
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
