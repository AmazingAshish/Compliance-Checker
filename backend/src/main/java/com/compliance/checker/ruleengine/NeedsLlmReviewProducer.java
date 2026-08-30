package com.compliance.checker.ruleengine;

import com.compliance.checker.common.config.CompliancePipelineProperties;
import com.compliance.checker.common.event.NeedsLlmReviewEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NeedsLlmReviewProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CompliancePipelineProperties props;

    public void publish(NeedsLlmReviewEvent event) {
        String topic = props.getKafka().getTopics().getNeedsLlmReview();
        kafkaTemplate.send(topic, event.documentId().toString(), event);
        log.info("Published NeedsLlmReviewEvent for document {} to topic {} ({} ambiguous rule(s))",
                event.documentId(), topic, event.ambiguousRuleResultIds().size());
    }
}
