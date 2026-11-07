package com.scan.api.infrastructure.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DocumentSearchFragmentImplTest {

    private final EntityManager em = mock(EntityManager.class);
    @SuppressWarnings("unchecked")
    private final TypedQuery<DocumentJpaEntity> pageQuery = mock(TypedQuery.class);
    @SuppressWarnings("unchecked")
    private final TypedQuery<Long> countQuery = mock(TypedQuery.class);
    private final DocumentSearchFragmentImpl fragment = new DocumentSearchFragmentImpl(em);

    private void stub() {
        when(em.createQuery(contains("select d from"), eq(DocumentJpaEntity.class))).thenReturn(pageQuery);
        when(em.createQuery(contains("select count(d)"), eq(Long.class))).thenReturn(countQuery);
        when(pageQuery.setParameter(anyString(), any())).thenReturn(pageQuery);
        when(pageQuery.setFirstResult(anyInt())).thenReturn(pageQuery);
        when(pageQuery.setMaxResults(anyInt())).thenReturn(pageQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
    }

    @Test
    void lit_la_page_demandee_et_le_total() {
        DocumentJpaEntity e = DocumentJpaEntity.builder().id(UUID.randomUUID()).originalFilename("a.pdf").size(1)
                .contentType("application/pdf").sha256("s").s3Key("k").createdAt(Instant.EPOCH).build();
        stub();
        when(pageQuery.getResultList()).thenReturn(List.of(e));
        when(countQuery.getSingleResult()).thenReturn(42L);

        DocumentSearchFragment.SearchResult res = fragment.search(false, "%a%", 30, 10);

        assertThat(res.items()).containsExactly(e);
        assertThat(res.total()).isEqualTo(42);
        verify(pageQuery).setParameter("pattern", "%a%");
        verify(pageQuery).setFirstResult(30);
        verify(pageQuery).setMaxResults(10);
        verify(countQuery).setParameter("pattern", "%a%");
    }

    @Test
    void filtre_selon_l_etat_d_archivage_et_trie_de_facon_stable() {
        stub();
        when(pageQuery.getResultList()).thenReturn(List.of());
        when(countQuery.getSingleResult()).thenReturn(0L);
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);

        fragment.search(true, "%%", 0, 20);
        fragment.search(false, "%%", 0, 20);

        verify(em, times(2)).createQuery(jpql.capture(), eq(DocumentJpaEntity.class));
        assertThat(jpql.getAllValues().get(0)).contains("d.archivedAt is not null");
        assertThat(jpql.getAllValues().get(1)).contains("d.archivedAt is null");
        assertThat(jpql.getAllValues()).allSatisfy(q -> assertThat(q)
                .contains("like :pattern escape '!'")
                .contains("order by d.createdAt desc, d.id desc"));
    }
}
