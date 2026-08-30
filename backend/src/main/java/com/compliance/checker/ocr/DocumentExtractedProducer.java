package com.compliance.checker.ocr;

import com.compliance.checker.common.config.CompliancePipelineProperties;
import com.compliance.checker.common.event.DocumentExtractedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentExtractedProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CompliancePipelineProperties props;

    public void publish(DocumentExtractedEvent event) {
        String topic = props.getKafka().getTopics().getDocumentExtracted();
        kafkaTemplate.send(topic, event.documentId().toString(), event);
        log.info("Published DocumentExtractedEvent for document {} to topic {}", event.documentId(), topic);
    }
}
