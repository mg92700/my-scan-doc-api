package com.scan.api.domain.model;

/**
 * Erreur du stockage objet (indisponible, timeout, objet absent…).
 * Traduite en 503 par la couche web : le client peut réessayer.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
