package com.compliance.checker.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Outcome of a single {@link com.compliance.checker.ruleengine.ComplianceRule} evaluation for a document. */
@Entity
@Table(name = "rule_results")
@Getter
@Setter
@NoArgsConstructor
public class RuleResult {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Verdict verdict;

    private double confidence;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private Instant evaluatedAt = Instant.now();
}
