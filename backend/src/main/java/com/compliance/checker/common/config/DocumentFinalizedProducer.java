package com.compliance.checker.common.config;

import com.compliance.checker.common.event.DocumentFinalizedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Shared by both the rule-engine consumer (confident path) and the LLM-review consumer (escalated path). */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentFinalizedProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CompliancePipelineProperties props;

    public void publish(DocumentFinalizedEvent event) {
        String topic = props.getKafka().getTopics().getDocumentFinalized();
        kafkaTemplate.send(topic, event.documentId().toString(), event);
        log.info("Published DocumentFinalizedEvent for document {} verdict={} source={}",
                event.documentId(), event.verdict(), event.source());
    }
}
