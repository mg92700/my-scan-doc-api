package com.scan.api.infrastructure.adapter.out.conversion;

import com.scan.api.domain.model.ConversionException;
import com.scan.api.domain.port.out.ImageToPdfPort;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Adapter sortant — convertit une image en PDF avec Apache PDFBox.
 *
 * - Une image = une page A4 (portrait ou paysage selon l'image), image centrée et mise à l'échelle.
 * - Sans perte : un JPEG est intégré tel quel ; PNG, GIF, BMP et TIFF sont intégrés en compression sans perte.
 * - Toute impossibilité (format sans lecteur, fichier corrompu, image trop grande) est signalée par une
 *   {@link ConversionException} : le service archive alors l'image d'origine dans un ZIP.
 */
@Component
public class PdfBoxImageToPdfConverter implements ImageToPdfPort {

    /**
     * Au-delà de ce nombre de pixels, l'image n'est pas décodée (protection contre les images « bombes »
     * qui saturent la mémoire : une image décompressée occupe plusieurs octets par pixel).
     */
    static final long MAX_PIXELS = 20_000_000L;

    @Override
    public byte[] toPdf(byte[] image) {
        checkDimensions(image);
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDImageXObject pdfImage = PDImageXObject.createFromByteArray(document, image, "image");
            PDRectangle format = pdfImage.getWidth() > pdfImage.getHeight()
                    ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()) // paysage
                    : PDRectangle.A4;

            float scale = Math.min(format.getWidth() / pdfImage.getWidth(),
                                   format.getHeight() / pdfImage.getHeight());
            float width = pdfImage.getWidth() * scale;
            float height = pdfImage.getHeight() * scale;

            PDPage page = new PDPage(format);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(pdfImage,
                        (format.getWidth() - width) / 2, (format.getHeight() - height) / 2, width, height);
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            throw new ConversionException("Conversion en PDF impossible", e);
        }
    }

    /** Lit seulement l'en-tête de l'image : refuse les formats inconnus et les images trop grandes. */
    private static void checkDimensions(byte[] image) {
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(image))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new ConversionException("Format d'image non pris en charge");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new ConversionException(
                            "Image trop grande (" + pixels + " pixels, maximum " + MAX_PIXELS + ")");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ConversionException("Image illisible", e);
        }
    }
}
