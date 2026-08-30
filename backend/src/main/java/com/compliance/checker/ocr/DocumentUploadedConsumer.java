package com.compliance.checker.ocr;

import com.compliance.checker.audit.AuditLogService;
import com.compliance.checker.common.entity.Document;
import com.compliance.checker.common.entity.DocumentStatus;
import com.compliance.checker.common.entity.ExtractionResult;
import com.compliance.checker.common.event.DocumentExtractedEvent;
import com.compliance.checker.common.event.DocumentUploadedEvent;
import com.compliance.checker.common.repository.DocumentRepository;
import com.compliance.checker.common.repository.ExtractionResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Consumes document-uploaded, runs OCR/text extraction, and publishes
 * document-extracted. Resilience contract for this stage:
 *  - Idempotent: if an ExtractionResult already exists for this document
 *    (e.g. this message is a Kafka at-least-once redelivery), skip re-processing
 *    instead of doing duplicate work or double-publishing downstream.
 *  - Never crashes the consumer thread: any exception during extraction is
 *    caught, the document is marked EXTRACTION_FAILED with an audit entry
 *    explaining why, and the offset is still committed so one bad document
 *    doesn't block the partition forever.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadedConsumer {

    private final OcrService ocrService;
    private final DocumentRepository documentRepository;
    private final ExtractionResultRepository extractionResultRepository;
    private final DocumentExtractedProducer producer;
    private final AuditLogService auditLogService;

    @KafkaListener(topics = "${compliance.kafka.topics.document-uploaded}", groupId = "ocr-service")
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        if (extractionResultRepository.existsByDocumentId(event.documentId())) {
            log.info("Extraction already exists for document {}, skipping duplicate delivery", event.documentId());
            return;
        }

        Optional<Document> maybeDocument = documentRepository.findById(event.documentId());
        if (maybeDocument.isEmpty()) {
            log.error("Received document-uploaded event for unknown document {}", event.documentId());
            return;
        }
        Document document = maybeDocument.get();

        try {
            document.setStatus(DocumentStatus.EXTRACTING);
            documentRepository.save(document);

            OcrService.ExtractionOutcome outcome = ocrService.extract(Path.of(event.storedPath()), event.contentType());

            ExtractionResult result = new ExtractionResult();
            result.setDocumentId(event.documentId());
            result.setExtractedText(outcome.text());
            result.setExtractionMethod(outcome.method());
            extractionResultRepository.save(result);

            document.setStatus(DocumentStatus.EXTRACTED);
            documentRepository.save(document);
            auditLogService.record(event.documentId(), DocumentStatus.EXTRACTED, "ocr",
                    "Extracted " + outcome.text().length() + " characters via " + outcome.method() + ".");

            producer.publish(new DocumentExtractedEvent(event.documentId(), outcome.text()));
        } catch (Exception e) {
            log.error("Extraction failed for document {}", event.documentId(), e);
            document.setStatus(DocumentStatus.EXTRACTION_FAILED);
            documentRepository.save(document);
            auditLogService.record(event.documentId(), DocumentStatus.EXTRACTION_FAILED, "ocr",
                    "Extraction failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            // Deliberately swallowed: a bad file must not kill this consumer thread
            // or block subsequent partitions/messages from being processed.
        }
    }
}
