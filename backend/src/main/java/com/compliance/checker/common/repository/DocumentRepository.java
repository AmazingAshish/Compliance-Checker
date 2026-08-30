package com.compliance.checker.common.repository;

import com.compliance.checker.common.entity.Document;
import com.compliance.checker.common.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {
    long countByStatus(DocumentStatus status);
}
