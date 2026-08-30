package com.compliance.checker.ocr;

import com.compliance.checker.common.config.CompliancePipelineProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Tika text-layer extraction path (no native Tesseract binary
 * required, since a "PDF" with a substantial amount of parseable text never
 * falls through to the OCR fallback branch). Tesseract itself is a thin,
 * mostly-native wrapper (tess4j) that isn't meaningfully unit-testable
 * without a real installed binary + trained data, which is why the design
 * puts the "which extractor to use" decision behind the OcrService interface
 * -- callers like DocumentUploadedConsumer are tested against a mock of that
 * interface instead (see the Kafka consumer test).
 */
class TikaTesseractOcrServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsTextViaTikaWhenDocumentHasATextLayer() throws Exception {
        CompliancePipelineProperties props = new CompliancePipelineProperties();
        TikaTesseractOcrService service = new TikaTesseractOcrService(props);

        Path file = tempDir.resolve("sample.txt");
        String content = "Reference REF1234567A, account 4400 1122 3399 0071, dated 2026-01-15. "
                + "This is a compliant onboarding document with a real text layer.";
        Files.writeString(file, content);

        // Tika's AutoDetectParser handles plain text fine even though we label it as a PDF
        // content-type here; what we're validating is the "Tika succeeded, don't fall back
        // to OCR" branch of extract().
        OcrService.ExtractionOutcome outcome = service.extract(file, "application/pdf");

        assertThat(outcome.method()).isEqualTo("TIKA");
        assertThat(outcome.text()).contains("REF1234567A");
    }
}
