package com.compliance.checker.ruleengine.rules;

import com.compliance.checker.ruleengine.ComplianceRule;
import com.compliance.checker.ruleengine.RuleEvaluation;
import org.springframework.stereotype.Component;

/**
 * Basic OCR/scan quality check (policy OPS-410). Very short or noise-heavy
 * extracted text is treated as an extraction-quality problem in the first
 * instance, not an automatic compliance failure.
 */
@Component
public class MinimumContentQualityRule implements ComplianceRule {

    private static final int MIN_LENGTH = 50;
    private static final double MAX_NOISE_RATIO = 0.35;

    @Override
    public String getName() {
        return "minimum-content-quality";
    }

    @Override
    public RuleEvaluation evaluate(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return RuleEvaluation.fail(getName(), 0.9, "No text could be extracted from the document at all.");
        }

        String trimmed = extractedText.trim();
        if (trimmed.length() < MIN_LENGTH) {
            return RuleEvaluation.uncertain(getName(), 0.5,
                    "Extracted text is only " + trimmed.length()
                            + " characters, below the " + MIN_LENGTH
                            + "-character quality floor; likely a poor scan (policy OPS-410).");
        }

        long nonAlnum = trimmed.chars().filter(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c)).count();
        double noiseRatio = (double) nonAlnum / trimmed.length();
        if (noiseRatio > MAX_NOISE_RATIO) {
            return RuleEvaluation.uncertain(getName(), 0.5,
                    String.format("Extracted text is %.0f%% non-alphanumeric noise, consistent with a garbled OCR scan (policy OPS-410).",
                            noiseRatio * 100));
        }

        return RuleEvaluation.pass(getName(), 0.85, "Extracted text length and character composition look reasonable.");
    }
}
