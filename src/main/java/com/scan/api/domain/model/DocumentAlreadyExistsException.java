package com.scan.api.domain.model;

/**
 * Levée par le port de persistance quand un document de même empreinte SHA-256
 * existe déjà (ex : deux uploads concurrents du même fichier).
 */
public class DocumentAlreadyExistsException extends RuntimeException {

    private final String sha256;

    public DocumentAlreadyExistsException(String sha256, Throwable cause) {
        super("Document déjà existant: sha256=" + sha256, cause);
        this.sha256 = sha256;
    }

    public String getSha256() { return sha256; }
}
