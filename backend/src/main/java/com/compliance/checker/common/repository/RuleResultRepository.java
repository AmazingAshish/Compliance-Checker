package com.compliance.checker.common.repository;

import com.compliance.checker.common.entity.RuleResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RuleResultRepository extends JpaRepository<RuleResult, UUID> {
    List<RuleResult> findByDocumentId(UUID documentId);
    boolean existsByDocumentId(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
