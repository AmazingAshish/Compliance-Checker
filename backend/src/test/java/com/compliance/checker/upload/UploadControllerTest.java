package com.compliance.checker.upload;

import com.compliance.checker.common.entity.Document;
import com.compliance.checker.common.entity.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc test for the upload endpoint: verifies a valid PDF is accepted (202/201)
 * and an unsupported content type is rejected, without touching Kafka/Postgres.
 */
@WebMvcTest(UploadController.class)
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @Test
    void acceptsAValidPdfUpload() throws Exception {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setOriginalFilename("policy.pdf");
        document.setStatus(DocumentStatus.UPLOADED);
        when(documentService.handleUpload(any())).thenReturn(document);

        MockMultipartFile file = new MockMultipartFile("file", "policy.pdf", "application/pdf", "dummy-pdf-bytes".getBytes());

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("policy.pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    void rejectsAnEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        when(documentService.handleUpload(any()))
                .thenThrow(new DocumentService.UnsupportedDocumentTypeException("Unsupported content type 'text/plain'."));

        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isUnsupportedMediaType());
    }
}
