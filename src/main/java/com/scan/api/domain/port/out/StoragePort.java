package com.scan.api.domain.port.out;

import java.io.InputStream;

/**
 * Port sortant — abstraction du stockage objet.
 * L'implémentation S3 est dans infrastructure/adapter/out/storage/.
 */
public interface StoragePort {

    /**
     * Stocke les bytes et retourne la clé unique générée (ex: "2025/03/18/uuid.pdf").
     */
    String store(InputStream input, long size, String contentType, String suggestedExt);

    /**
     * Retourne les bytes associés à une clé.
     */
    byte[] get(String key);
}
