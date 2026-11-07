package com.scan.api.application.service;

import java.util.List;

/**
 * Reconnaissance du type d'un fichier par ses premiers octets (« signature »).
 * Le Content-Type et l'extension envoyés par le client ne sont jamais utilisés : ils sont falsifiables.
 */
final class FileSignature {

    private static final List<String> HEIF_BRANDS = List.of("heic", "heix", "hevc", "hevx", "mif1", "msf1", "avif");

    private FileSignature() {
    }

    static boolean isPdf(byte[] b) {
        return at(b, 0, '%', 'P', 'D', 'F', '-');
    }

    /** JPEG, PNG, GIF, BMP, TIFF, WebP et HEIF/HEIC/AVIF. */
    static boolean isImage(byte[] b) {
        return at(b, 0, 0xFF, 0xD8, 0xFF)                                          // JPEG
                || at(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)           // PNG
                || at(b, 0, 'G', 'I', 'F', '8')                                    // GIF
                || (at(b, 0, 'B', 'M') && at(b, 6, 0, 0, 0, 0))                    // BMP (champs réservés à zéro)
                || at(b, 0, 'I', 'I', 0x2A, 0x00)                                  // TIFF petit-boutiste
                || at(b, 0, 'M', 'M', 0x00, 0x2A)                                  // TIFF gros-boutiste
                || (at(b, 0, 'R', 'I', 'F', 'F') && at(b, 8, 'W', 'E', 'B', 'P'))  // WebP
                || isHeif(b);                                                      // HEIC / HEIF / AVIF
    }

    private static boolean isHeif(byte[] b) {
        if (!at(b, 4, 'f', 't', 'y', 'p')) return false;
        for (String brand : HEIF_BRANDS) {
            if (at(b, 8, brand.chars().toArray())) return true;
        }
        return false;
    }

    private static boolean at(byte[] data, int offset, int... signature) {
        if (data.length < offset + signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((data[offset + i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }
}
