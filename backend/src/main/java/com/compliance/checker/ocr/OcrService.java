package com.compliance.checker.ocr;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction over text extraction so the Kafka consumer can be unit tested
 * with a mock instead of exercising real Tika/Tesseract binaries.
 */
public interface OcrService {

    ExtractionOutcome extract(Path filePath, String contentType) throws IOException;

    record ExtractionOutcome(String text, String method) {
    }
}
