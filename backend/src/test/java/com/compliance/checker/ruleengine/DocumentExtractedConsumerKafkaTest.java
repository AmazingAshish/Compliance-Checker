package com.compliance.checker.ruleengine;

import com.compliance.checker.common.entity.Document;
import com.compliance.checker.common.entity.DocumentStatus;
import com.compliance.checker.common.event.DocumentExtractedEvent;
import com.compliance.checker.common.repository.DocumentRepository;
import com.compliance.checker.common.repository.RuleResultRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the rule-engine consumer against a real (embedded)
 * Kafka broker: publishes a DocumentExtractedEvent and asserts the consumer
 * evaluates the rules, persists RuleResults, and finalizes the document.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"document-extracted", "needs-llm-review", "document-finalized"})
@TestPropertySource(properties = {
        // Dummy key so Spring AI's Google GenAI autoconfiguration wires beans normally; the
        // PolicyVectorStoreInitializer's real embedding call will fail without network
        // access in CI, but that failure is caught and logged (see its try/catch),
        // it does not fail application context startup or this test.
        "spring.ai.google.genai.api-key=test-key-not-real",
        "spring.ai.google.genai.embedding.api-key=test-key-not-real",
        "compliance.confidence-threshold=0.75"
})
class DocumentExtractedConsumerKafkaTest {

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        // spring.embedded.kafka.brokers is set automatically by @EmbeddedKafka via the test system property;
        // Spring Boot's spring.kafka.bootstrap-servers picks it up through the "spring.embedded.kafka.brokers" placeholder.
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:kafkatest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    }

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private RuleResultRepository ruleResultRepository;

    private KafkaTemplate<String, Object> testProducer(String bootstrapServers) {
        Map<String, Object> props = KafkaTestUtils.producerProps(bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        ProducerFactory<String, Object> pf = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(pf);
    }

    @Test
    void confidentDocumentIsFinalizedByTheRuleEngineConsumer() {
        Document document = new Document();
        document.setOriginalFilename("clean.pdf");
        document.setStoredPath("/tmp/clean.pdf");
        document.setContentType("application/pdf");
        document.setStatus(DocumentStatus.EXTRACTED);
        document = documentRepository.save(document);

        String bootstrapServers = System.getProperty("spring.embedded.kafka.brokers");
        KafkaTemplate<String, Object> producer = testProducer(bootstrapServers);

        String cleanText = "Reference REF7788321A, account 4400 1122 3399 0071, dated 2026-01-15. "
                + "This is a clean, compliant onboarding document with plenty of extracted text.";

        producer.send("document-extracted", document.getId().toString(),
                new DocumentExtractedEvent(document.getId(), cleanText));

        UUID id = document.getId();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Document reloaded = documentRepository.findById(id).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.FINALIZED);
            assertThat(ruleResultRepository.findByDocumentId(id)).isNotEmpty();
        });
    }
}
