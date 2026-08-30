package com.compliance.checker.common.event;

import java.io.Serializable;
import java.util.UUID;

/** Published to the {@code document-uploaded} topic right after a Document row is persisted. */
public record DocumentUploadedEvent(UUID documentId, String storedPath, String contentType) implements Serializable {
}
