package com.compliance.checker.dashboard;

import com.compliance.checker.audit.AuditLogService;
import com.compliance.checker.common.entity.Document;
import com.compliance.checker.common.entity.DocumentStatus;
import com.compliance.checker.common.repository.DocumentRepository;
import com.compliance.checker.common.repository.ExtractionResultRepository;
import com.compliance.checker.common.repository.LlmReviewResultRepository;
import com.compliance.checker.common.repository.RuleResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Query + deletion endpoints backing the React dashboard's list and detail views. */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentQueryController {

    private final DocumentRepository documentRepository;
    private final ExtractionResultRepository extractionResultRepository;
    private final RuleResultRepository ruleResultRepository;
    private final LlmReviewResultRepository llmReviewResultRepository;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<DocumentSummaryDto> list(
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) String filename,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return documentRepository.findAll(
                        DocumentSpecifications.withFilters(status, filename, from, to),
                        Sort.by(Sort.Direction.DESC, "uploadedAt"))
                .stream()
                .map(DocumentSummaryDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDetailDto> detail(@PathVariable UUID id) {
        return documentRepository.findById(id)
                .map(this::toDetailDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Deletes a document and every child record (extraction result, rule
     * results, LLM review, audit trail) plus its stored file on disk. This is
     * a hard delete - there's no soft-delete/undo, since the resulting empty
     * state (document simply gone) is what a compliance reviewer clearing out
     * test uploads actually wants. Wrapped in one transaction so a failure
     * partway through doesn't leave orphaned child rows.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Document document = documentRepository.findById(id).orElse(null);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }

        extractionResultRepository.deleteByDocumentId(id);
        ruleResultRepository.deleteByDocumentId(id);
        llmReviewResultRepository.deleteByDocumentId(id);
        auditLogService.deleteHistory(id);
        documentRepository.delete(document);

        deleteStoredFile(document.getStoredPath());

        return ResponseEntity.noContent().build();
    }

    private void deleteStoredFile(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(storedPath));
        } catch (IOException e) {
            // Non-fatal: the DB records are already gone (source of truth for the
            // dashboard), so an orphaned file on disk is a cleanup nuisance, not a
            // reason to fail the delete the user is waiting on.
            log.warn("Could not delete stored file {}", storedPath, e);
        }
    }

    private DocumentDetailDto toDetailDto(Document document) {
        String extractedText = extractionResultRepository.findFirstByDocumentIdOrderByExtractedAtDesc(document.getId())
                .map(er -> er.getExtractedText())
                .orElse(null);

        return DocumentDetailDto.from(
                document,
                extractedText,
                ruleResultRepository.findByDocumentId(document.getId()),
                llmReviewResultRepository.findByDocumentId(document.getId()),
                auditLogService.history(document.getId())
        );
    }
}
