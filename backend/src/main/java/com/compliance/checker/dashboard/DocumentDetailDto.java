package com.compliance.checker.dashboard;

import com.compliance.checker.common.entity.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentDetailDto(
        UUID id,
        String filename,
        String contentType,
        long fileSizeBytes,
        DocumentStatus status,
        Verdict finalVerdict,
        Instant uploadedAt,
        Instant updatedAt,
        String extractedText,
        List<RuleResult> ruleResults,
        List<LlmReviewResult> llmReviewResults,
        List<AuditLogEntry> auditTrail
) {
    public static DocumentDetailDto from(Document d, String extractedText, List<RuleResult> ruleResults,
                                          List<LlmReviewResult> llmReviewResults, List<AuditLogEntry> auditTrail) {
        return new DocumentDetailDto(d.getId(), d.getOriginalFilename(), d.getContentType(), d.getFileSizeBytes(),
                d.getStatus(), d.getFinalVerdict(), d.getUploadedAt(), d.getUpdatedAt(),
                extractedText, ruleResults, llmReviewResults, auditTrail);
    }
}
