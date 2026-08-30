package com.compliance.checker.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single uploaded document (PDF or image) moving through the compliance
 * pipeline. This is the aggregate root the whole dashboard is built around.
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String storedPath;

    @Column(nullable = false)
    private String contentType;

    private long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    /** Final PASS/FAIL/UNCERTAIN once the pipeline completes. Null while in flight. */
    @Enumerated(EnumType.STRING)
    private Verdict finalVerdict;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
