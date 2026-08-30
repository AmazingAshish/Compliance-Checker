package com.compliance.checker.common.repository;

import com.compliance.checker.common.entity.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, UUID> {
    List<AuditLogEntry> findByDocumentIdOrderByTimestampAsc(UUID documentId);
}
