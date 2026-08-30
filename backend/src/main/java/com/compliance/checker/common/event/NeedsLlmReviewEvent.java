package com.compliance.checker.common.event;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Published to the {@code needs-llm-review} topic when the rule engine cannot
 * confidently resolve one or more rules. Carries the extracted text plus the
 * specific ambiguous rule result ids so the LLM consumer doesn't have to guess
 * which rule(s) triggered escalation.
 */
public record NeedsLlmReviewEvent(UUID documentId,
                                   String extractedText,
                                   List<UUID> ambiguousRuleResultIds) implements Serializable {
}
