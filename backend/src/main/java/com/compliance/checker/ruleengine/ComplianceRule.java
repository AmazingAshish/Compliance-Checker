package com.compliance.checker.ruleengine;

/**
 * A single, independent, deterministic compliance check. Implementations must
 * be stateless and side-effect free so they can be run in any order and unit
 * tested in isolation (see RuleEngineServiceTest, which runs every rule
 * table-driven over a set of fixtures).
 */
public interface ComplianceRule {

    /** Stable, human-readable name used for persistence, audit trails, and dashboard display. */
    String getName();

    /** Evaluate this rule against the full extracted text of a document. */
    RuleEvaluation evaluate(String extractedText);
}
