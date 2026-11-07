package com.scan.api.infrastructure.adapter.out.persistence;

import java.util.List;

/** Recherche paginée par décalage (offset) que Spring Data ne sait pas exprimer seul. */
interface DocumentSearchFragment {

    record SearchResult(List<DocumentJpaEntity> items, long total) {}

    /**
     * @param pattern motif LIKE déjà en minuscules et échappé avec {@code !} (voir {@code DocumentJpaAdapter})
     */
    SearchResult search(boolean archived, String pattern, int offset, int limit);
}
