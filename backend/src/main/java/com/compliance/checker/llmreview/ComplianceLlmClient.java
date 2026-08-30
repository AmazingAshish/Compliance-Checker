package com.compliance.checker.llmreview;

import com.compliance.checker.common.entity.Verdict;

import java.util.List;

/**
 * Thin wrapper around Spring AI's ChatClient. Exists as an interface so
 * LlmReviewService can be unit tested without making a real network call to
 * an LLM provider.
 */
public interface ComplianceLlmClient {

    LlmVerdict review(String extractedText, List<String> ambiguousRuleDescriptions, List<String> retrievedPolicyContext);

    record LlmVerdict(Verdict verdict, String reasoning) {
    }
}
