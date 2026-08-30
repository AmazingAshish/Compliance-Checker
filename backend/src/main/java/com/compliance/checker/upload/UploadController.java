package com.compliance.checker.upload;

import com.compliance.checker.common.entity.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File must not be empty"));
        }
        try {
            Document document = documentService.handleUpload(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(UploadResponse.from(document));
        } catch (DocumentService.UnsupportedDocumentTypeException e) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of("error", e.getMessage()));
        }
    }

    public record UploadResponse(UUID id, String filename, String status) {
        static UploadResponse from(Document d) {
            return new UploadResponse(d.getId(), d.getOriginalFilename(), d.getStatus().name());
        }
    }
}
