package com.compliance.checker.llmreview;

import com.compliance.checker.audit.AuditLogService;
import com.compliance.checker.common.config.DocumentFinalizedProducer;
import com.compliance.checker.common.entity.Document;
import com.compliance.checker.common.entity.DocumentStatus;
import com.compliance.checker.common.entity.LlmReviewResult;
import com.compliance.checker.common.entity.RuleResult;
import com.compliance.checker.common.event.DocumentFinalizedEvent;
import com.compliance.checker.common.event.NeedsLlmReviewEvent;
import com.compliance.checker.common.repository.DocumentRepository;
import com.compliance.checker.common.repository.LlmReviewResultRepository;
import com.compliance.checker.common.repository.RuleResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consumes needs-llm-review, retrieves the ambiguous RuleResult rows, calls
 * the RAG-grounded LlmReviewService, persists the LlmReviewResult (linked
 * back to the triggering rule results for auditability), and finalizes the
 * document.
 *
 * Same idempotency/resilience contract as the other stage consumers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NeedsLlmReviewConsumer {

    private final LlmReviewService llmReviewService;
    private final DocumentRepository documentRepository;
    private final RuleResultRepository ruleResultRepository;
    private final LlmReviewResultRepository llmReviewResultRepository;
    private final DocumentFinalizedProducer documentFinalizedProducer;
    private final AuditLogService auditLogService;

    @KafkaListener(topics = "${compliance.kafka.topics.needs-llm-review}", groupId = "llm-review-service")
    public void onNeedsLlmReview(NeedsLlmReviewEvent event) {
        if (llmReviewResultRepository.existsByDocumentId(event.documentId())) {
            log.info("LLM review already exists for document {}, skipping duplicate delivery", event.documentId());
            return;
        }

        Optional<Document> maybeDocument = documentRepository.findById(event.documentId());
        if (maybeDocument.isEmpty()) {
            log.error("Received needs-llm-review event for unknown document {}", event.documentId());
            return;
        }
        Document document = maybeDocument.get();

        try {
            document.setStatus(DocumentStatus.LLM_REVIEWING);
            documentRepository.save(document);

            List<RuleResult> ambiguousRules = event.ambiguousRuleResultIds().stream()
                    .map(id -> ruleResultRepository.findById(id).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .toList();

            LlmReviewService.ReviewOutcome outcome = llmReviewService.review(event.extractedText(), ambiguousRules);

            LlmReviewResult reviewResult = new LlmReviewResult();
            reviewResult.setDocumentId(event.documentId());
            reviewResult.setTriggeringRuleResultIds(event.ambiguousRuleResultIds().stream()
                    .map(UUID::toString).collect(Collectors.joining(",")));
            reviewResult.setVerdict(outcome.verdict());
            reviewResult.setReasoning(outcome.reasoning());
            reviewResult.setRetrievedContext(String.join("\n---\n", outcome.retrievedContext()));
            reviewResult.setModelUsed("spring-ai-google-genai");
            llmReviewResultRepository.save(reviewResult);

            document.setStatus(DocumentStatus.LLM_REVIEWED);
            documentRepository.save(document);
            auditLogService.record(event.documentId(), DocumentStatus.LLM_REVIEWED, "llmreview",
                    "LLM verdict=" + outcome.verdict() + ". " + outcome.reasoning());

            document.setStatus(DocumentStatus.FINALIZED);
            document.setFinalVerdict(outcome.verdict());
            documentRepository.save(document);
            auditLogService.record(event.documentId(), DocumentStatus.FINALIZED, "llmreview",
                    "Finalized with verdict " + outcome.verdict() + " (LLM-assisted).");

            documentFinalizedProducer.publish(new DocumentFinalizedEvent(event.documentId(), outcome.verdict(), "llm-review"));
        } catch (Exception e) {
            log.error("LLM review failed for document {}", event.documentId(), e);
            document.setStatus(DocumentStatus.LLM_REVIEW_FAILED);
            documentRepository.save(document);
            auditLogService.record(event.documentId(), DocumentStatus.LLM_REVIEW_FAILED, "llmreview",
                    "LLM review failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()
                            + ". Check GEMINI_API_KEY is set correctly.");
        }
    }
}
