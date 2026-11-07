package com.scan.api.infrastructure.adapter.out.persistence;

import jakarta.persistence.EntityManager;

import java.util.List;

/**
 * Deux requêtes paramétrées (jamais de texte concaténé) : la page demandée, puis le total.
 * Le tri {@code createdAt desc, id desc} est total, donc deux pages ne se chevauchent jamais.
 */
class DocumentSearchFragmentImpl implements DocumentSearchFragment {

    private static final String FROM = " from DocumentJpaEntity d where d.archivedAt ";
    private static final String FILTER = " and lower(d.originalFilename) like :pattern escape '!'";

    private final EntityManager em;

    DocumentSearchFragmentImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public SearchResult search(boolean archived, String pattern, int offset, int limit) {
        String state = archived ? "is not null" : "is null";

        List<DocumentJpaEntity> items = em
                .createQuery("select d" + FROM + state + FILTER + " order by d.createdAt desc, d.id desc",
                        DocumentJpaEntity.class)
                .setParameter("pattern", pattern)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();

        long total = em
                .createQuery("select count(d)" + FROM + state + FILTER, Long.class)
                .setParameter("pattern", pattern)
                .getSingleResult();

        return new SearchResult(items, total);
    }
}
