package com.compliance.checker.ruleengine.rules;

import com.compliance.checker.common.config.CompliancePipelineProperties;
import com.compliance.checker.ruleengine.ComplianceRule;
import com.compliance.checker.ruleengine.RuleEvaluation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Watchlist/banned-terms check (policy ECC-220). A hit is never an automatic
 * FAIL by itself - context matters (a due-diligence memo warning against a
 * shell company is legitimate) - so this rule always returns UNCERTAIN on a
 * hit and routes to LLM review, and PASS only when no banned term appears.
 */
@Component
public class BannedTermsRule implements ComplianceRule {

    private final List<String> bannedTerms;

    public BannedTermsRule(CompliancePipelineProperties props) {
        this.bannedTerms = props.getBannedTerms();
    }

    @Override
    public String getName() {
        return "banned-watchlist-terms";
    }

    @Override
    public RuleEvaluation evaluate(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return RuleEvaluation.uncertain(getName(), 0.4, "No text to scan for watchlist terms.");
        }

        String lower = extractedText.toLowerCase(Locale.ROOT);
        for (String term : bannedTerms) {
            if (lower.contains(term.toLowerCase(Locale.ROOT))) {
                return RuleEvaluation.uncertain(getName(), 0.5,
                        "Document contains watchlist term '" + term
                                + "'. Context must be reviewed to determine whether it describes/warns against, "
                                + "versus facilitates, a prohibited structure (policy ECC-220).");
            }
        }
        return RuleEvaluation.pass(getName(), 0.95, "No banned/watchlist terms found.");
    }
}
