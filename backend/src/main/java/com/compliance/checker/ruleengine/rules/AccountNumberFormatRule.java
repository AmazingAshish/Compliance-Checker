package com.compliance.checker.ruleengine.rules;

import com.compliance.checker.ruleengine.ComplianceRule;
import com.compliance.checker.ruleengine.RuleEvaluation;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Basic format/regex check: if a grouped numeric sequence resembling an account
 * number is present (policy KYC-115), verify it isn't obviously malformed
 * (e.g. all-zero, wrong length after stripping separators).
 */
@Component
public class AccountNumberFormatRule implements ComplianceRule {

    private static final Pattern GROUPED_NUMBER = Pattern.compile("\\b(?:\\d[\\d -]{6,20}\\d)\\b");

    @Override
    public String getName() {
        return "account-number-format";
    }

    @Override
    public RuleEvaluation evaluate(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return RuleEvaluation.uncertain(getName(), 0.4, "No text to validate account number format against.");
        }

        Matcher matcher = GROUPED_NUMBER.matcher(extractedText);
        if (!matcher.find()) {
            // Absence of a numeric identifier is covered by ReferenceNumberPresenceRule;
            // this rule only judges the format of what IS present.
            return RuleEvaluation.pass(getName(), 0.7, "No grouped numeric account number found to validate; not applicable.");
        }

        String raw = matcher.group();
        String digitsOnly = raw.replaceAll("[^0-9]", "");

        if (digitsOnly.length() < 8 || digitsOnly.length() > 16) {
            return RuleEvaluation.fail(getName(), 0.8,
                    "Candidate account number '" + raw + "' has " + digitsOnly.length()
                            + " digits, outside the valid 8-16 digit range.");
        }
        if (digitsOnly.chars().distinct().count() == 1) {
            return RuleEvaluation.fail(getName(), 0.9,
                    "Candidate account number '" + raw + "' is a repeated-digit sequence, almost certainly invalid.");
        }
        return RuleEvaluation.pass(getName(), 0.9, "Account number '" + raw + "' matches expected numeric format.");
    }
}
