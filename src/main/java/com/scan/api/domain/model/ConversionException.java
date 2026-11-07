package com.scan.api.domain.model;

/**
 * Une image n'a pas pu être convertie en PDF (format non pris en charge, fichier corrompu,
 * dimensions excessives…). Le service la conserve alors telle quelle dans une archive ZIP.
 */
public class ConversionException extends RuntimeException {

    public ConversionException(String message) {
        super(message);
    }

    public ConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
