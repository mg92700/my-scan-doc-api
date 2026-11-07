package com.scan.api.infrastructure.adapter.out.conversion;

import com.scan.api.domain.model.ConversionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfBoxImageToPdfConverterTest {

    private final PdfBoxImageToPdfConverter converter = new PdfBoxImageToPdfConverter();

    private static BufferedImage image(int largeur, int hauteur) {
        BufferedImage img = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < largeur; x++) {
            for (int y = 0; y < hauteur; y++) {
                img.setRGB(x, y, new Color((x * 7) % 256, (y * 11) % 256, (x + y) % 256).getRGB());
            }
        }
        return img;
    }

    private static byte[] encoder(BufferedImage img, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(img, format, out)).as("écrivain " + format + " disponible").isTrue();
        return out.toByteArray();
    }

    private static PDImageXObject imageIntegree(PDDocument document) throws IOException {
        PDResources ressources = document.getPage(0).getResources();
        COSName nom = ressources.getXObjectNames().iterator().next();
        return (PDImageXObject) ressources.getXObject(nom);
    }

    @Test
    void png_converti_sans_perte_de_pixels() throws Exception {
        BufferedImage source = image(40, 30);

        byte[] pdf = converter.toPdf(encoder(source, "png"));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            PDImageXObject integree = imageIntegree(document);
            assertThat(integree.getWidth()).isEqualTo(40);
            assertThat(integree.getHeight()).isEqualTo(30);
            BufferedImage relue = integree.getImage();
            for (int x = 0; x < 40; x++) {
                for (int y = 0; y < 30; y++) {
                    assertThat(relue.getRGB(x, y) & 0xFFFFFF).isEqualTo(source.getRGB(x, y) & 0xFFFFFF);
                }
            }
        }
    }

    @Test
    void jpeg_integre_sans_re_encodage() throws Exception {
        byte[] jpeg = encoder(image(64, 48), "jpg");

        byte[] pdf = converter.toPdf(jpeg);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDImageXObject integree = imageIntegree(document);
            assertThat(integree.getSuffix()).isEqualTo("jpg"); // flux JPEG d'origine (DCTDecode)
            assertThat(integree.getWidth()).isEqualTo(64);
            assertThat(integree.getHeight()).isEqualTo(48);
        }
    }

    @Test
    void gif_et_bmp_convertis() throws Exception {
        for (String format : new String[]{"gif", "bmp"}) {
            byte[] pdf = converter.toPdf(encoder(image(20, 20), format));
            try (PDDocument document = Loader.loadPDF(pdf)) {
                assertThat(document.getNumberOfPages()).as(format).isEqualTo(1);
            }
        }
    }

    @Test
    void page_paysage_pour_une_image_large() throws Exception {
        byte[] pdf = converter.toPdf(encoder(image(80, 40), "png"));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDRectangle page = document.getPage(0).getMediaBox();
            assertThat(page.getWidth()).isEqualTo(PDRectangle.A4.getHeight());
            assertThat(page.getHeight()).isEqualTo(PDRectangle.A4.getWidth());
        }
    }

    @Test
    void page_portrait_pour_une_image_haute() throws Exception {
        byte[] pdf = converter.toPdf(encoder(image(40, 80), "png"));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDRectangle page = document.getPage(0).getMediaBox();
            assertThat(page.getWidth()).isEqualTo(PDRectangle.A4.getWidth());
            assertThat(page.getHeight()).isEqualTo(PDRectangle.A4.getHeight());
        }
    }

    @Test
    void octets_quelconques_refuses() {
        assertThatThrownBy(() -> converter.toPdf("hello world".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("non pris en charge");
    }

    @Test
    void format_sans_lecteur_refuse() {
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 contenu".getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> converter.toPdf(webp))
                .isInstanceOf(ConversionException.class);
    }

    @Test
    void image_trop_grande_refusee_avant_decodage() throws Exception {
        // 8000 x 7000 = 56 millions de pixels (1 bit par pixel : le PNG reste léger), au-dessus de la limite
        BufferedImage grande = new BufferedImage(8000, 7000, BufferedImage.TYPE_BYTE_BINARY);

        assertThatThrownBy(() -> converter.toPdf(encoder(grande, "png")))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("trop grande");
    }

    @Test
    void png_tronque_refuse() throws Exception {
        byte[] png = encoder(image(40, 30), "png");

        assertThatThrownBy(() -> converter.toPdf(Arrays.copyOf(png, 60)))
                .isInstanceOf(ConversionException.class);
    }
}
