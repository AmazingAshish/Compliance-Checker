package com.compliance.checker.llmreview;

import com.compliance.checker.common.entity.Verdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * {@code @Lazy}: without a GEMINI_API_KEY, Spring AI's Google GenAI
 * auto-configuration throws building the underlying ChatModel. This bean
 * (and the {@link ChatClient.Builder} it depends on) must not be
 * instantiated until an LLM review actually runs, so a missing key doesn't
 * crash application startup - the failure then lands inside
 * NeedsLlmReviewConsumer's try/catch, matching the pipeline's per-stage
 * resilience pattern instead of taking down the whole app.
 */

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Lazy
@Slf4j
public class SpringAiComplianceLlmClient implements ComplianceLlmClient {

    private static final String SYSTEM_PROMPT = """
            You are a compliance review assistant for a financial-services document
            compliance pipeline. A deterministic rule engine flagged one or more rules
            as UNCERTAIN or below its confidence threshold. Your job is to make the
            final call, grounded ONLY in the policy passages provided below - do not
            rely on outside/general knowledge of compliance regulation.

            Respond in exactly this format:
            VERDICT: <PASS|FAIL|UNCERTAIN>
            REASONING: <plain-language explanation a non-technical compliance officer can follow,
            citing which policy passage(s) informed the decision>
            """;

    private static final Pattern VERDICT_PATTERN = Pattern.compile("VERDICT:\\s*(PASS|FAIL|UNCERTAIN)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REASONING_PATTERN = Pattern.compile("REASONING:\\s*(.*)", Pattern.DOTALL);

    private final ChatClient chatClient;

    public SpringAiComplianceLlmClient(@Lazy ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public LlmVerdict review(String extractedText, List<String> ambiguousRuleDescriptions, List<String> retrievedPolicyContext) {
        String userPrompt = buildUserPrompt(extractedText, ambiguousRuleDescriptions, retrievedPolicyContext);

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        return parse(response);
    }

    private String buildUserPrompt(String extractedText, List<String> ambiguousRuleDescriptions, List<String> retrievedPolicyContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("AMBIGUOUS RULE FINDINGS:\n");
        ambiguousRuleDescriptions.forEach(d -> sb.append("- ").append(d).append('\n'));

        sb.append("\nRELEVANT POLICY PASSAGES:\n");
        for (int i = 0; i < retrievedPolicyContext.size(); i++) {
            sb.append("[Passage ").append(i + 1).append("]\n").append(retrievedPolicyContext.get(i)).append('\n');
        }

        sb.append("\nEXTRACTED DOCUMENT TEXT:\n").append(extractedText);
        return sb.toString();
    }

    private LlmVerdict parse(String response) {
        if (response == null) {
            return new LlmVerdict(Verdict.UNCERTAIN, "LLM returned an empty response.");
        }

        Matcher verdictMatcher = VERDICT_PATTERN.matcher(response);
        Verdict verdict = Verdict.UNCERTAIN;
        if (verdictMatcher.find()) {
            verdict = Verdict.valueOf(verdictMatcher.group(1).toUpperCase(Locale.ROOT));
        } else {
            log.warn("Could not parse VERDICT from LLM response, defaulting to UNCERTAIN: {}", response);
        }

        Matcher reasoningMatcher = REASONING_PATTERN.matcher(response);
        String reasoning = reasoningMatcher.find() ? reasoningMatcher.group(1).trim() : response.trim();

        return new LlmVerdict(verdict, reasoning);
    }
}
