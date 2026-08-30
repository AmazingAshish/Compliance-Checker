package com.compliance.checker.upload;

import com.compliance.checker.audit.AuditLogService;
import com.compliance.checker.common.config.CompliancePipelineProperties;
import com.compliance.checker.common.entity.Document;
import com.compliance.checker.common.entity.DocumentStatus;
import com.compliance.checker.common.event.DocumentUploadedEvent;
import com.compliance.checker.common.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the upload endpoint's business logic: persist the file to disk,
 * create the Document row, write the initial audit entry, and hand off to
 * Kafka. Kept separate from the controller so it's unit-testable without
 * standing up MockMvc.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg");

    private final DocumentRepository documentRepository;
    private final DocumentUploadedProducer producer;
    private final AuditLogService auditLogService;
    private final CompliancePipelineProperties props;

    public Document handleUpload(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new UnsupportedDocumentTypeException(
                    "Unsupported content type '" + contentType + "'. Only PDF, PNG, and JPEG are accepted.");
        }

        Path storedPath = storeFile(file);

        Document document = new Document();
        document.setOriginalFilename(file.getOriginalFilename());
        document.setStoredPath(storedPath.toString());
        document.setContentType(contentType);
        document.setFileSizeBytes(file.getSize());
        document.setStatus(DocumentStatus.UPLOADED);
        document = documentRepository.save(document);

        auditLogService.record(document.getId(), DocumentStatus.UPLOADED, "upload",
                "Document '" + document.getOriginalFilename() + "' uploaded (" + document.getFileSizeBytes() + " bytes).");

        producer.publish(new DocumentUploadedEvent(document.getId(), document.getStoredPath(), document.getContentType()));

        return document;
    }

    private Path storeFile(MultipartFile file) {
        try {
            Path uploadDir = Path.of(props.getStorage().getUploadDir());
            Files.createDirectories(uploadDir);
            String safeName = UUID.randomUUID() + "-" + sanitize(file.getOriginalFilename());
            Path target = uploadDir.resolve(safeName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }
    }

    private String sanitize(String filename) {
        if (filename == null) {
            return "unnamed";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public List<Document> findAll() {
        return documentRepository.findAll();
    }

    public static class UnsupportedDocumentTypeException extends RuntimeException {
        public UnsupportedDocumentTypeException(String message) {
            super(message);
        }
    }
}
