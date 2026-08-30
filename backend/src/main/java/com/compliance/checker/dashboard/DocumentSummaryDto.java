package com.compliance.checker.dashboard;

import com.compliance.checker.common.entity.Document;
import com.compliance.checker.common.entity.DocumentStatus;
import com.compliance.checker.common.entity.Verdict;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummaryDto(UUID id, String filename, DocumentStatus status, Verdict finalVerdict, Instant uploadedAt) {
    public static DocumentSummaryDto from(Document d) {
        return new DocumentSummaryDto(d.getId(), d.getOriginalFilename(), d.getStatus(), d.getFinalVerdict(), d.getUploadedAt());
    }
}
