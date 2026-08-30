package com.compliance.checker.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of one state transition for a document. Written by every
 * consumer as it processes a message so the full lifecycle can be reconstructed
 * for compliance/audit purposes, independent of the mutable {@link Document#status}.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLogEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus toStatus;

    @Column(nullable = false)
    private String stage;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant timestamp = Instant.now();

    public AuditLogEntry(UUID documentId, DocumentStatus toStatus, String stage, String detail) {
        this.documentId = documentId;
        this.toStatus = toStatus;
        this.stage = stage;
        this.detail = detail;
    }
}
