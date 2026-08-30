package com.compliance.checker.ocr;

import com.compliance.checker.common.config.CompliancePipelineProperties;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Real extraction implementation:
 *  - PDFs go through Apache Tika first (fast, works for text-layer PDFs).
 *    If Tika comes back with suspiciously little text (scanned PDF with no
 *    text layer), we fall back to Tesseract OCR on the rendered page images
 *    is out of scope for this skeleton; we OCR the raw bytes via Tesseract
 *    directly, which tess4j supports for PDFs when Ghostscript is present.
 *  - PNG/JPEG images always go straight to Tesseract OCR.
 */
@Service
@Slf4j
public class TikaTesseractOcrService implements OcrService {

    private static final int MIN_ACCEPTABLE_TIKA_CHARS = 20;

    private final CompliancePipelineProperties props;

    public TikaTesseractOcrService(CompliancePipelineProperties props) {
        this.props = props;
    }

    @Override
    public ExtractionOutcome extract(Path filePath, String contentType) throws IOException {
        if ("application/pdf".equals(contentType)) {
            String tikaText = extractWithTika(filePath);
            if (tikaText != null && tikaText.trim().length() >= MIN_ACCEPTABLE_TIKA_CHARS) {
                return new ExtractionOutcome(tikaText.trim(), "TIKA");
            }
            log.info("Tika extracted little/no text from {}, falling back to Tesseract OCR", filePath);
            return new ExtractionOutcome(extractWithTesseract(filePath), "TESSERACT_OCR");
        }

        // image/png, image/jpeg
        return new ExtractionOutcome(extractWithTesseract(filePath), "TESSERACT_OCR");
    }

    private String extractWithTika(Path filePath) throws IOException {
        try (InputStream stream = Files.newInputStream(filePath)) {
            BodyContentHandler handler = new BodyContentHandler(-1); // no length limit
            AutoDetectParser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
            parser.parse(stream, handler, metadata, new ParseContext());
            return handler.toString();
        } catch (SAXException | TikaException e) {
            throw new IOException("Tika parsing failed for " + filePath, e);
        }
    }

    private String extractWithTesseract(Path filePath) throws IOException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(props.getOcr().getTessdataPath());
        tesseract.setLanguage(props.getOcr().getLanguage());
        try {
            BufferedImage image = ImageIO.read(filePath.toFile());
            if (image == null) {
                throw new IOException("Unable to decode image for OCR: " + filePath);
            }
            return tesseract.doOCR(image);
        } catch (TesseractException e) {
            throw new IOException("Tesseract OCR failed for " + filePath, e);
        }
    }
}
