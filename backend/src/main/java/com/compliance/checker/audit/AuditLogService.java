package com.compliance.checker.audit;

import com.compliance.checker.common.entity.AuditLogEntry;
import com.compliance.checker.common.entity.DocumentStatus;
import com.compliance.checker.common.repository.AuditLogEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Central write path for the audit trail. Every consumer/service calls this instead of touching the repository directly. */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogEntryRepository repository;

    public AuditLogEntry record(UUID documentId, DocumentStatus toStatus, String stage, String detail) {
        return repository.save(new AuditLogEntry(documentId, toStatus, stage, detail));
    }

    public List<AuditLogEntry> history(UUID documentId) {
        return repository.findByDocumentIdOrderByTimestampAsc(documentId);
    }
}
