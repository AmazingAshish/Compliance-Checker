package com.compliance.checker.ruleengine.rules;

import com.compliance.checker.ruleengine.ComplianceRule;
import com.compliance.checker.ruleengine.RuleEvaluation;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Required-field-presence + validity check: the document must contain at least one parseable date. */
@Component
public class DateValidityRule implements ComplianceRule {

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}/\\d{4}|\\d{1,2}-\\d{1,2}-\\d{4})\\b");

    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
    };

    @Override
    public String getName() {
        return "date-validity";
    }

    @Override
    public RuleEvaluation evaluate(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return RuleEvaluation.uncertain(getName(), 0.4, "No text to search for a document date.");
        }

        Matcher matcher = DATE_PATTERN.matcher(extractedText);
        while (matcher.find()) {
            String candidate = matcher.group();
            for (DateTimeFormatter formatter : FORMATTERS) {
                try {
                    LocalDate.parse(candidate, formatter);
                    return RuleEvaluation.pass(getName(), 0.9, "Found valid, parseable date '" + candidate + "'.");
                } catch (DateTimeParseException ignored) {
                    // try next formatter
                }
            }
        }
        return RuleEvaluation.fail(getName(), 0.75, "No parseable date found in the document text.");
    }
}
