package com.compliance.checker.common.repository;

import com.compliance.checker.common.entity.LlmReviewResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LlmReviewResultRepository extends JpaRepository<LlmReviewResult, UUID> {
    List<LlmReviewResult> findByDocumentId(UUID documentId);
    boolean existsByDocumentId(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
