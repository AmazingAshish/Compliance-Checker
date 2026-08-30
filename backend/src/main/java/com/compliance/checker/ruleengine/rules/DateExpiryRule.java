package com.compliance.checker.ruleengine.rules;

import com.compliance.checker.ruleengine.ComplianceRule;
import com.compliance.checker.ruleengine.RuleEvaluation;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expiry check (policy DOC-330): a document dated more than 18 months ago is
 * "stale" and should be routed to review rather than hard-failed, since
 * renewal paperwork is often legitimately back-dated.
 */
@Component
public class DateExpiryRule implements ComplianceRule {

    private static final int STALE_AFTER_MONTHS = 18;

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}/\\d{4}|\\d{1,2}-\\d{1,2}-\\d{4})\\b");

    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
    };

    @Override
    public String getName() {
        return "date-expiry";
    }

    @Override
    public RuleEvaluation evaluate(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return RuleEvaluation.uncertain(getName(), 0.4, "No text to check document date against expiry policy.");
        }

        LocalDate mostRecent = findMostRecentParseableDate(extractedText);
        if (mostRecent == null) {
            // No date at all is a stronger signal than a stale one (policy DOC-330).
            return RuleEvaluation.uncertain(getName(), 0.5,
                    "No date found; cannot evaluate expiry. Weighted per policy DOC-330 as needing review.");
        }

        long monthsOld = ChronoUnit.MONTHS.between(mostRecent, LocalDate.now());
        if (monthsOld < 0) {
            return RuleEvaluation.uncertain(getName(), 0.5,
                    "Document date '" + mostRecent + "' is in the future; needs human/LLM confirmation.");
        }
        if (monthsOld > STALE_AFTER_MONTHS) {
            return RuleEvaluation.uncertain(getName(), 0.6,
                    "Document dated '" + mostRecent + "' is " + monthsOld
                            + " months old, exceeding the " + STALE_AFTER_MONTHS
                            + "-month staleness window; review recommended per policy DOC-330.");
        }
        return RuleEvaluation.pass(getName(), 0.9, "Document date '" + mostRecent + "' is within the valid window.");
    }

    private LocalDate findMostRecentParseableDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        LocalDate latest = null;
        while (matcher.find()) {
            String candidate = matcher.group();
            for (DateTimeFormatter formatter : FORMATTERS) {
                try {
                    LocalDate parsed = LocalDate.parse(candidate, formatter);
                    if (latest == null || parsed.isAfter(latest)) {
                        latest = parsed;
                    }
                    break;
                } catch (DateTimeParseException ignored) {
                    // try next formatter
                }
            }
        }
        return latest;
    }
}
