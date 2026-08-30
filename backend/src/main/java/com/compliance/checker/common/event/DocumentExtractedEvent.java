package com.compliance.checker.common.event;

import java.io.Serializable;
import java.util.UUID;

/** Published to the {@code document-extracted} topic once OCR/text extraction succeeds. */
public record DocumentExtractedEvent(UUID documentId, String extractedText) implements Serializable {
}
