package com.compliance.checker.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Binds the {@code compliance.*} block from application.yml. */
@Component
@ConfigurationProperties(prefix = "compliance")
@Getter
@Setter
public class CompliancePipelineProperties {

    /** Every rule's confidence must be >= this, and none may be UNCERTAIN, to skip LLM review. */
    private double confidenceThreshold = 0.75;

    private List<String> bannedTerms = new ArrayList<>();

    private Kafka kafka = new Kafka();
    private Storage storage = new Storage();
    private Ocr ocr = new Ocr();

    @Getter
    @Setter
    public static class Kafka {
        private Topics topics = new Topics();
    }

    @Getter
    @Setter
    public static class Topics {
        private String documentUploaded = "document-uploaded";
        private String documentExtracted = "document-extracted";
        private String needsLlmReview = "needs-llm-review";
        private String documentFinalized = "document-finalized";
    }

    @Getter
    @Setter
    public static class Storage {
        private String uploadDir = "./uploads";
    }

    @Getter
    @Setter
    public static class Ocr {
        private String tessdataPath = "./tessdata";
        private String language = "eng";
    }
}
