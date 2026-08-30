package com.compliance.checker.ruleengine;

import com.compliance.checker.common.config.CompliancePipelineProperties;
import com.compliance.checker.common.entity.Verdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Runs every registered {@link ComplianceRule} against a document's extracted
 * text and decides whether the result is confident enough to be final, or
 * must be escalated to LLM review.
 *
 * Aggregation policy: the verdict is final only if EVERY rule's confidence is
 * >= the configured threshold AND no rule returned UNCERTAIN. Otherwise the
 * whole set of rule results is returned to the caller (the ruleengine Kafka
 * consumer), which is responsible for publishing needs-llm-review with the
 * specific ambiguous rules attached.
 */
@Service
@Slf4j
public class RuleEngineService {

    private final List<ComplianceRule> rules;
    private final double confidenceThreshold;

    public RuleEngineService(List<ComplianceRule> rules, CompliancePipelineProperties props) {
        this.rules = rules;
        this.confidenceThreshold = props.getConfidenceThreshold();
        log.info("RuleEngineService initialised with {} rules, confidence threshold={}", rules.size(), confidenceThreshold);
    }

    public AggregatedResult evaluate(String extractedText) {
        List<RuleEvaluation> evaluations = rules.stream()
                .map(rule -> safeEvaluate(rule, extractedText))
                .toList();

        boolean anyUncertain = evaluations.stream().anyMatch(e -> e.verdict() == Verdict.UNCERTAIN);
        boolean allConfident = evaluations.stream().allMatch(e -> e.confidence() >= confidenceThreshold);
        boolean confidentEnough = allConfident && !anyUncertain;

        Verdict aggregateVerdict = null;
        if (confidentEnough) {
            aggregateVerdict = evaluations.stream().anyMatch(e -> e.verdict() == Verdict.FAIL)
                    ? Verdict.FAIL
                    : Verdict.PASS;
        }

        return new AggregatedResult(evaluations, confidentEnough, aggregateVerdict, confidenceThreshold);
    }

    /** A single misbehaving rule (e.g. a bug, an NPE on unexpected input) must never take down the whole engine. */
    private RuleEvaluation safeEvaluate(ComplianceRule rule, String extractedText) {
        try {
            return rule.evaluate(extractedText);
        } catch (Exception e) {
            log.error("Rule '{}' threw an exception during evaluation; treating as UNCERTAIN", rule.getName(), e);
            return RuleEvaluation.uncertain(rule.getName(), 0.0,
                    "Rule execution failed with " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public record AggregatedResult(List<RuleEvaluation> evaluations, boolean confidentEnough, Verdict finalVerdict,
                                    double confidenceThreshold) {
        /** Rules that either returned UNCERTAIN or fell below the confidence threshold - the ones LLM review needs to look at. */
        public List<RuleEvaluation> ambiguousEvaluations() {
            return evaluations.stream()
                    .filter(e -> e.verdict() == Verdict.UNCERTAIN || e.confidence() < confidenceThreshold)
                    .toList();
        }
    }
}
