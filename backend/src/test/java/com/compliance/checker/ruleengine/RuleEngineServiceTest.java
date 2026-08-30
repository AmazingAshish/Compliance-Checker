package com.compliance.checker.ruleengine;

import com.compliance.checker.common.config.CompliancePipelineProperties;
import com.compliance.checker.common.entity.Verdict;
import com.compliance.checker.ruleengine.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Table-driven test over all 6 concrete rules plus the aggregation logic in
 * RuleEngineService (confidence threshold + "any UNCERTAIN forces escalation").
 */
class RuleEngineServiceTest {

    private RuleEngineService buildEngine() {
        CompliancePipelineProperties props = new CompliancePipelineProperties();
        props.setConfidenceThreshold(0.75);
        props.setBannedTerms(List.of("shell company", "sanctioned jurisdiction"));

        List<ComplianceRule> rules = List.of(
                new ReferenceNumberPresenceRule(),
                new AccountNumberFormatRule(),
                new BannedTermsRule(props),
                new DateValidityRule(),
                new DateExpiryRule(),
                new MinimumContentQualityRule()
        );
        return new RuleEngineService(rules, props);
    }

    static Stream<Arguments> individualRuleCases() {
        return Stream.of(
                Arguments.of(new ReferenceNumberPresenceRule(),
                        "Reference number REF7788321A on file, dated 2026-01-15.", Verdict.PASS),
                Arguments.of(new ReferenceNumberPresenceRule(),
                        "This document has no identifying numbers whatsoever.", Verdict.FAIL),
                Arguments.of(new AccountNumberFormatRule(),
                        "Account 4400 1122 3399 0071 confirmed.", Verdict.PASS),
                Arguments.of(new AccountNumberFormatRule(),
                        "Account 0000000000 confirmed.", Verdict.FAIL),
                Arguments.of(new DateValidityRule(),
                        "Signed on 2026-01-15 by the account holder.", Verdict.PASS),
                Arguments.of(new DateValidityRule(),
                        "No date mentioned anywhere in this text.", Verdict.FAIL),
                Arguments.of(new MinimumContentQualityRule(), "short", Verdict.UNCERTAIN),
                Arguments.of(new MinimumContentQualityRule(),
                        "This is a perfectly ordinary, reasonably long block of extracted document text with real words.",
                        Verdict.PASS)
        );
    }

    @ParameterizedTest
    @MethodSource("individualRuleCases")
    void ruleReturnsExpectedVerdict(ComplianceRule rule, String text, Verdict expected) {
        RuleEvaluation evaluation = rule.evaluate(text);
        assertThat(evaluation.verdict()).isEqualTo(expected);
        assertThat(evaluation.confidence()).isBetween(0.0, 1.0);
        assertThat(evaluation.reason()).isNotBlank();
    }

    @Test
    void bannedTermHitIsAlwaysUncertainNeverAutoFail() {
        BannedTermsRule rule = new BannedTermsRule(propsWithBannedTerms());
        RuleEvaluation evaluation = rule.evaluate("This memo warns clients against using a shell company structure.");
        assertThat(evaluation.verdict()).isEqualTo(Verdict.UNCERTAIN);
    }

    @Test
    void cleanConfidentDocumentIsFinalizedWithoutLlmEscalation() {
        RuleEngineService engine = buildEngine();
        String text = "Reference REF7788321A, account 4400 1122 3399 0071, dated 2026-01-15. "
                + "This is a clean, compliant onboarding document with plenty of extracted text.";

        RuleEngineService.AggregatedResult result = engine.evaluate(text);

        assertThat(result.confidentEnough()).isTrue();
        assertThat(result.finalVerdict()).isEqualTo(Verdict.PASS);
        assertThat(result.ambiguousEvaluations()).isEmpty();
    }

    @Test
    void ambiguousDocumentEscalatesToLlmReview() {
        RuleEngineService engine = buildEngine();
        String text = "This document discusses a shell company arrangement, dated 2026-01-15, ref REF1122334A.";

        RuleEngineService.AggregatedResult result = engine.evaluate(text);

        assertThat(result.confidentEnough()).isFalse();
        assertThat(result.finalVerdict()).isNull();
        assertThat(result.ambiguousEvaluations()).isNotEmpty();
    }

    @Test
    void emptyDocumentIsNotConfidentEnoughToAutoFinalize() {
        RuleEngineService engine = buildEngine();
        RuleEngineService.AggregatedResult result = engine.evaluate("");
        assertThat(result.confidentEnough()).isFalse();
    }

    private CompliancePipelineProperties propsWithBannedTerms() {
        CompliancePipelineProperties props = new CompliancePipelineProperties();
        props.setBannedTerms(List.of("shell company", "sanctioned jurisdiction"));
        return props;
    }
}
