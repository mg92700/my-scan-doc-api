package com.scan.api.domain.port.in;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentPage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Port entrant (driving port) — définit ce que l'application sait faire.
 * Implémenté par DocumentService dans application/service/.
 * Appelé par les adapters REST, CLI, tests…
 */
public interface DocumentUseCase {

    /**
     * Valide, dédoublonne et stocke un document reçu.
     * <ul>
     *   <li>PDF : stocké tel quel ;</li>
     *   <li>image : convertie en PDF, ou, si ce n'est pas possible, archivée telle quelle dans un ZIP ;</li>
     *   <li>tout autre format est refusé ({@link IllegalArgumentException}).</li>
     * </ul>
     * Le type est reconnu par le contenu du fichier, jamais par le Content-Type déclaré.
     */
    Document save(String originalFilename, String contentType,
                     InputStream in, long size) throws IOException;

    /**
     * Récupère les bytes d'un document depuis le stockage objet.
     */
    DownloadResult download(UUID id);

    /**
     * Liste une page de documents actifs ou archivés, filtrés par nom, du plus récent au plus ancien.
     *
     * @param query  texte cherché dans le nom (vide = tous), 100 caractères maximum
     * @param offset rang du premier résultat, 0 ou plus
     * @param limit  nombre de résultats, de 1 à 100
     * @throws IllegalArgumentException si un paramètre est hors limites
     */
    DocumentPage list(boolean archived, String query, int offset, int limit);

    /** Archive un document (idempotent). @throws com.scan.api.domain.model.DocumentNotFoundException si absent */
    Document archive(UUID id);

    /** Restaure un document archivé (idempotent). @throws com.scan.api.domain.model.DocumentNotFoundException si absent */
    Document unarchive(UUID id);

    /** Supprime définitivement le document (base puis stockage objet). @throws com.scan.api.domain.model.DocumentNotFoundException si absent */
    void delete(UUID id);

    record DownloadResult(String filename, String contentType, byte[] bytes) {}
}
