package com.compliance.checker.common.event;

import com.compliance.checker.common.entity.Verdict;

import java.io.Serializable;
import java.util.UUID;

/** Published to {@code document-finalized} once a document has a terminal verdict, for any downstream consumers (e.g. notifications). */
public record DocumentFinalizedEvent(UUID documentId, Verdict verdict, String source) implements Serializable {
}
