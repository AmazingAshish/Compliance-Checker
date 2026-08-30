package com.compliance.checker.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * LLM-produced verdict for a document that the rule engine could not confidently
 * resolve on its own. Linked back to the originating {@link RuleResult} (by id)
 * so an auditor can trace exactly which rule triggered the escalation and why
 * the LLM overrode/confirmed it.
 */
@Entity
@Table(name = "llm_review_results")
@Getter
@Setter
@NoArgsConstructor
public class LlmReviewResult {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    /** The RuleResult(s) that were UNCERTAIN / triggered escalation, comma-separated ids. */
    @Column(nullable = false)
    private String triggeringRuleResultIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Verdict verdict;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    /** Newline-joined snippets of the retrieved policy passages used to ground this verdict. */
    @Column(columnDefinition = "TEXT")
    private String retrievedContext;

    private String modelUsed;

    private Instant reviewedAt = Instant.now();
}
