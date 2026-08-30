package com.compliance.checker.ruleengine;

import com.compliance.checker.audit.AuditLogService;
import com.compliance.checker.common.config.DocumentFinalizedProducer;
import com.compliance.checker.common.entity.Document;
import com.compliance.checker.common.entity.DocumentStatus;
import com.compliance.checker.common.entity.RuleResult;
import com.compliance.checker.common.entity.Verdict;
import com.compliance.checker.common.event.DocumentExtractedEvent;
import com.compliance.checker.common.event.DocumentFinalizedEvent;
import com.compliance.checker.common.event.NeedsLlmReviewEvent;
import com.compliance.checker.common.repository.DocumentRepository;
import com.compliance.checker.common.repository.RuleResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumes document-extracted, runs the deterministic rule engine, and either
 * finalizes the document (all rules confident) or publishes needs-llm-review
 * (any rule UNCERTAIN or below the confidence threshold).
 *
 * Same resilience contract as {@link com.compliance.checker.ocr.DocumentUploadedConsumer}:
 * idempotent via existence check on RuleResult, and any exception is caught
 * and turned into a RULE_CHECK_FAILED status rather than crashing the thread.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentExtractedConsumer {

    private final RuleEngineService ruleEngineService;
    private final DocumentRepository documentRepository;
    private final RuleResultRepository ruleResultRepository;
    private final NeedsLlmReviewProducer needsLlmReviewProducer;
    private final DocumentFinalizedProducer documentFinalizedProducer;
    private final AuditLogService auditLogService;

    @KafkaListener(topics = "${compliance.kafka.topics.document-extracted}", groupId = "rule-engine-service")
    public void onDocumentExtracted(DocumentExtractedEvent event) {
        if (ruleResultRepository.existsByDocumentId(event.documentId())) {
            log.info("Rule results already exist for document {}, skipping duplicate delivery", event.documentId());
            return;
        }

        Optional<Document> maybeDocument = documentRepository.findById(event.documentId());
        if (maybeDocument.isEmpty()) {
            log.error("Received document-extracted event for unknown document {}", event.documentId());
            return;
        }
        Document document = maybeDocument.get();

        try {
            document.setStatus(DocumentStatus.RULE_CHECKING);
            documentRepository.save(document);

            RuleEngineService.AggregatedResult result = ruleEngineService.evaluate(event.extractedText());

            List<RuleResult> persisted = result.evaluations().stream()
                    .map(evaluation -> persist(event.documentId(), evaluation))
                    .toList();

            auditLogService.record(event.documentId(), DocumentStatus.RULE_CHECKED, "ruleengine",
                    "Evaluated " + persisted.size() + " rule(s); confidentEnough=" + result.confidentEnough());

            if (result.confidentEnough()) {
                document.setStatus(DocumentStatus.FINALIZED);
                document.setFinalVerdict(result.finalVerdict());
                documentRepository.save(document);
                auditLogService.record(event.documentId(), DocumentStatus.FINALIZED, "ruleengine",
                        "Finalized with verdict " + result.finalVerdict() + " (rule engine confident, no LLM needed).");
                documentFinalizedProducer.publish(new DocumentFinalizedEvent(event.documentId(), result.finalVerdict(), "rule-engine"));
            } else {
                List<UUID> ambiguousIds = persisted.stream()
                        .filter(r -> matchesAmbiguousRuleName(result, r.getRuleName()))
                        .map(RuleResult::getId)
                        .toList();

                document.setStatus(DocumentStatus.NEEDS_LLM_REVIEW);
                documentRepository.save(document);
                auditLogService.record(event.documentId(), DocumentStatus.NEEDS_LLM_REVIEW, "ruleengine",
                        ambiguousIds.size() + " rule result(s) below confidence threshold or UNCERTAIN.");

                needsLlmReviewProducer.publish(new NeedsLlmReviewEvent(event.documentId(), event.extractedText(), ambiguousIds));
            }
        } catch (Exception e) {
            log.error("Rule evaluation failed for document {}", event.documentId(), e);
            document.setStatus(DocumentStatus.RULE_CHECK_FAILED);
            documentRepository.save(document);
            auditLogService.record(event.documentId(), DocumentStatus.RULE_CHECK_FAILED, "ruleengine",
                    "Rule evaluation failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private boolean matchesAmbiguousRuleName(RuleEngineService.AggregatedResult result, String ruleName) {
        return result.ambiguousEvaluations().stream().anyMatch(e -> e.ruleName().equals(ruleName));
    }

    private RuleResult persist(UUID documentId, RuleEvaluation evaluation) {
        RuleResult entity = new RuleResult();
        entity.setDocumentId(documentId);
        entity.setRuleName(evaluation.ruleName());
        entity.setVerdict(evaluation.verdict());
        entity.setConfidence(evaluation.confidence());
        entity.setReason(evaluation.reason());
        return ruleResultRepository.save(entity);
    }
}
