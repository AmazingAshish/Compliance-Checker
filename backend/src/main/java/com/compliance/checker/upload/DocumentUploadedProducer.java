package com.compliance.checker.upload;

import com.compliance.checker.common.config.CompliancePipelineProperties;
import com.compliance.checker.common.event.DocumentUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadedProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CompliancePipelineProperties props;

    public void publish(DocumentUploadedEvent event) {
        String topic = props.getKafka().getTopics().getDocumentUploaded();
        kafkaTemplate.send(topic, event.documentId().toString(), event);
        log.info("Published DocumentUploadedEvent for document {} to topic {}", event.documentId(), topic);
    }
}
