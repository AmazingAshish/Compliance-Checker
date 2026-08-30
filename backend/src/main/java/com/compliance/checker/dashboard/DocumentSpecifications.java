package com.compliance.checker.dashboard;

import com.compliance.checker.common.entity.Document;
import com.compliance.checker.common.entity.DocumentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class DocumentSpecifications {

    private DocumentSpecifications() {
    }

    public static Specification<Document> withFilters(DocumentStatus status, String filename, Instant from, Instant to) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (status != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status));
            }
            if (filename != null && !filename.isBlank()) {
                predicates = cb.and(predicates, cb.like(cb.lower(root.get("originalFilename")), "%" + filename.toLowerCase() + "%"));
            }
            if (from != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("uploadedAt"), from));
            }
            if (to != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("uploadedAt"), to));
            }
            return predicates;
        };
    }
}
