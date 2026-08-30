package com.compliance.checker.common.entity;

/**
 * Lifecycle states a {@link Document} moves through. Every transition is
 * mirrored into {@link AuditLogEntry} by whichever consumer performs it.
 */
public enum DocumentStatus {
    UPLOADED,
    EXTRACTING,
    EXTRACTED,
    EXTRACTION_FAILED,
    RULE_CHECKING,
    RULE_CHECKED,
    RULE_CHECK_FAILED,
    NEEDS_LLM_REVIEW,
    LLM_REVIEWING,
    LLM_REVIEWED,
    LLM_REVIEW_FAILED,
    FINALIZED
}
