package com.scan.api.domain.model;

import java.util.UUID;

/**
 * Levée quand un document demandé n'existe pas.
 * Distincte d'IllegalArgumentException (entrée invalide → 400).
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID id) {
        super("Document introuvable: " + id);
    }
}
