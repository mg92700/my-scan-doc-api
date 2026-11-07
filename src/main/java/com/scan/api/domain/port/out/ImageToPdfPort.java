package com.scan.api.domain.port.out;

/**
 * Port sortant — conversion d'une image en document PDF.
 * L'implémentation (PDFBox) est dans infrastructure/adapter/out/conversion/.
 */
public interface ImageToPdfPort {

    /**
     * Place l'image, sans perte, sur une page PDF.
     *
     * @throws com.scan.api.domain.model.ConversionException si l'image est illisible,
     *         d'un format non pris en charge ou trop grande
     */
    byte[] toPdf(byte[] image);
}
