package com.compliance.checker.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Text extracted from a {@link Document} via Tika (native text) or Tesseract OCR (scanned/image). */
@Entity
@Table(name = "extraction_results")
@Getter
@Setter
@NoArgsConstructor
public class ExtractionResult {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    /** "TIKA" for native text layer extraction, "TESSERACT_OCR" for image/OCR extraction. */
    private String extractionMethod;

    private Instant extractedAt = Instant.now();
}
