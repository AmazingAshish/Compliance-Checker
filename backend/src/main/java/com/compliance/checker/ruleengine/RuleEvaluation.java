package com.compliance.checker.ruleengine;

import com.compliance.checker.common.entity.Verdict;

/** Pure result of one {@link ComplianceRule} evaluation, decoupled from JPA. */
public record RuleEvaluation(String ruleName, Verdict verdict, double confidence, String reason) {

    public RuleEvaluation {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0, was " + confidence);
        }
    }

    public static RuleEvaluation pass(String ruleName, double confidence, String reason) {
        return new RuleEvaluation(ruleName, Verdict.PASS, confidence, reason);
    }

    public static RuleEvaluation fail(String ruleName, double confidence, String reason) {
        return new RuleEvaluation(ruleName, Verdict.FAIL, confidence, reason);
    }

    public static RuleEvaluation uncertain(String ruleName, double confidence, String reason) {
        return new RuleEvaluation(ruleName, Verdict.UNCERTAIN, confidence, reason);
    }
}
