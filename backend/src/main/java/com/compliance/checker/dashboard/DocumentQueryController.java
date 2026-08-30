package com.compliance.checker.dashboard;

import com.compliance.checker.audit.AuditLogService;
import com.compliance.checker.common.entity.Document;
import com.compliance.checker.common.entity.DocumentStatus;
import com.compliance.checker.common.repository.DocumentRepository;
import com.compliance.checker.common.repository.ExtractionResultRepository;
import com.compliance.checker.common.repository.LlmReviewResultRepository;
import com.compliance.checker.common.repository.RuleResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only endpoints backing the React dashboard's list and detail views. */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
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
        return documentRepository.findAll(DocumentSpecifications.withFilters(status, filename, from, to))
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
