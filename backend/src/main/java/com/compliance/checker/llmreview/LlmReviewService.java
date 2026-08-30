package com.compliance.checker.llmreview;

import com.compliance.checker.common.entity.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the RAG-grounded LLM review: retrieve the most relevant policy
 * passages for the ambiguous rule(s) + extracted text, then delegate to the
 * LLM client for the actual verdict/reasoning.
 */
@Service
@Slf4j
public class LlmReviewService {

    private static final int TOP_K = 3;

    private final VectorStore vectorStore;
    private final ComplianceLlmClient llmClient;

    /**
     * {@code llmClient} is injected {@code @Lazy}: {@link SpringAiComplianceLlmClient}
     * is itself {@code @Lazy}, but that alone only defers pre-instantiation during
     * context refresh - it does NOT stop this (non-lazy) service's constructor from
     * eagerly resolving it as a hard dependency. Declaring the parameter {@code @Lazy}
     * here makes Spring inject a proxy instead, so the real Google GenAI client
     * (and its GEMINI_API_KEY check) isn't touched until review() actually runs.
     */
    public LlmReviewService(VectorStore vectorStore, @Lazy ComplianceLlmClient llmClient) {
        this.vectorStore = vectorStore;
        this.llmClient = llmClient;
    }

    public ReviewOutcome review(String extractedText, List<RuleResult> ambiguousRules) {
        List<String> ruleDescriptions = ambiguousRules.stream()
                .map(r -> String.format("Rule '%s' -> %s (confidence %.2f): %s",
                        r.getRuleName(), r.getVerdict(), r.getConfidence(), r.getReason()))
                .toList();

        String retrievalQuery = extractedText + "\n" + String.join("\n", ruleDescriptions);
        List<Document> retrieved = vectorStore.similaritySearch(
                SearchRequest.builder().query(retrievalQuery).topK(TOP_K).build());

        List<String> policyPassages = retrieved.stream().map(Document::getText).toList();

        ComplianceLlmClient.LlmVerdict verdict = llmClient.review(extractedText, ruleDescriptions, policyPassages);

        return new ReviewOutcome(verdict.verdict(), verdict.reasoning(), policyPassages);
    }

    public record ReviewOutcome(com.compliance.checker.common.entity.Verdict verdict, String reasoning, List<String> retrievedContext) {
    }
}
