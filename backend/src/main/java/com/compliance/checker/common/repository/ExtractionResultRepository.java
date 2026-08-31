package com.compliance.checker.common.repository;

import com.compliance.checker.common.entity.ExtractionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExtractionResultRepository extends JpaRepository<ExtractionResult, UUID> {
    List<ExtractionResult> findByDocumentId(UUID documentId);
    Optional<ExtractionResult> findFirstByDocumentIdOrderByExtractedAtDesc(UUID documentId);
    boolean existsByDocumentId(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
