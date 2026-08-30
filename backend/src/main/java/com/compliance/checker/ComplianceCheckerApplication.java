package com.compliance.checker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the AI-Assisted Document Compliance Checker.
 *
 * Pipeline overview (see README for the full architecture diagram):
 *   upload -> ocr -> ruleengine -> (llmreview if ambiguous) -> audit trail throughout
 *
 * Each stage below is deployed as an independent Kafka consumer so that a
 * slow/failing stage (e.g. OCR on a huge scanned PDF, or an LLM provider outage)
 * never blocks ingestion of new documents.
 */
@SpringBootApplication
public class ComplianceCheckerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplianceCheckerApplication.class, args);
    }
}
