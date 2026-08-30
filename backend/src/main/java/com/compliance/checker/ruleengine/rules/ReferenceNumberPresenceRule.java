package com.compliance.checker.ruleengine.rules;

import com.compliance.checker.ruleengine.ComplianceRule;
import com.compliance.checker.ruleengine.RuleEvaluation;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Required-field-presence check: every document must carry a reference number
 * of the form described in policy KYC-101 (e.g. REF7788321A, ACC-2024-00931).
 */
@Component
public class ReferenceNumberPresenceRule implements ComplianceRule {

    private static final Pattern REFERENCE_PATTERN =
            Pattern.compile("\\b(?:REF|ACC|TXN|INTL)[-]?[A-Z0-9-]{5,14}\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String getName() {
        return "reference-number-presence";
    }

    @Override
    public RuleEvaluation evaluate(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return RuleEvaluation.uncertain(getName(), 0.4,
                    "No text available to search for a reference/account number; likely an OCR quality issue.");
        }

        Matcher matcher = REFERENCE_PATTERN.matcher(extractedText);
        if (matcher.find()) {
            return RuleEvaluation.pass(getName(), 0.95,
                    "Found reference/account identifier '" + matcher.group() + "'.");
        }

        // A bare long numeric run can also plausibly be an account number (policy KYC-115).
        Matcher numeric = Pattern.compile("\\b\\d{8,16}\\b").matcher(extractedText);
        if (numeric.find()) {
            return RuleEvaluation.uncertain(getName(), 0.55,
                    "No prefixed reference number found, but a bare numeric sequence '" + numeric.group()
                            + "' may be an account number; needs human/LLM confirmation.");
        }

        return RuleEvaluation.fail(getName(), 0.85,
                "No reference or account number pattern found anywhere in the extracted text.");
    }
}
