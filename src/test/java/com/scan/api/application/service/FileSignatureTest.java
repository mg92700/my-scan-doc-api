package com.scan.api.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FileSignatureTest {

    static byte[] b(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }

    static Stream<Arguments> images() {
        return Stream.of(
                Arguments.of("JPEG", b(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10)),
                Arguments.of("PNG", concat(b(0x89), ascii("PNG"), b(0x0D, 0x0A, 0x1A, 0x0A))),
                Arguments.of("GIF", ascii("GIF89a")),
                Arguments.of("BMP", concat(ascii("BM"), b(0x36, 0, 0, 0, 0, 0, 0, 0, 0x36, 0))),
                Arguments.of("TIFF petit-boutiste", concat(ascii("II"), b(0x2A, 0))),
                Arguments.of("TIFF gros-boutiste", concat(ascii("MM"), b(0, 0x2A))),
                Arguments.of("WebP", concat(ascii("RIFF"), b(0, 0, 0, 0), ascii("WEBP"))),
                Arguments.of("HEIC", concat(b(0, 0, 0, 0x18), ascii("ftypheic"))),
                Arguments.of("AVIF", concat(b(0, 0, 0, 0x18), ascii("ftypavif"))));
    }

    static Stream<Arguments> autres() {
        return Stream.of(
                Arguments.of("texte", ascii("hello world")),
                Arguments.of("texte commençant par BM", ascii("BM texte quelconque")),
                Arguments.of("archive ZIP", b(0x50, 0x4B, 3, 4, 0, 0, 0, 0, 0, 0, 0, 0)),
                Arguments.of("exécutable", b(0x4D, 0x5A, 0x90, 0, 3, 0, 0, 0)),
                Arguments.of("RIFF autre que WebP", concat(ascii("RIFF"), b(0, 0, 0, 0), ascii("WAVE"))),
                Arguments.of("conteneur ftyp autre que HEIF", concat(b(0, 0, 0, 0x18), ascii("ftypmp42"))),
                Arguments.of("trop court", b(0xFF, 0xD8)),
                Arguments.of("vide", new byte[0]));
    }

    @ParameterizedTest(name = "{0} est une image")
    @MethodSource("images")
    void reconnait_les_images(String nom, byte[] octets) {
        assertThat(FileSignature.isImage(octets)).isTrue();
        assertThat(FileSignature.isPdf(octets)).isFalse();
    }

    @ParameterizedTest(name = "{0} n''est ni PDF ni image")
    @MethodSource("autres")
    void rejette_les_autres_formats(String nom, byte[] octets) {
        assertThat(FileSignature.isImage(octets)).isFalse();
        assertThat(FileSignature.isPdf(octets)).isFalse();
    }

    @Test
    void reconnait_un_pdf() {
        assertThat(FileSignature.isPdf(ascii("%PDF-1.4\n"))).isTrue();
        assertThat(FileSignature.isImage(ascii("%PDF-1.4\n"))).isFalse();
    }

    @Test
    void pdf_tronque_non_reconnu() {
        assertThat(FileSignature.isPdf(ascii("%PDF"))).isFalse();
    }
}
