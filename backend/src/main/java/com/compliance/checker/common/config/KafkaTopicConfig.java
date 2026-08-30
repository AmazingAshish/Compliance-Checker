package com.compliance.checker.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the pipeline's topics so Spring Kafka auto-creates them on startup
 * against a KRaft/broker that allows topic auto-creation from AdminClient beans
 * (docker-compose's single-node broker does).
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic documentUploadedTopic(CompliancePipelineProperties props) {
        return TopicBuilder.name(props.getKafka().getTopics().getDocumentUploaded()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic documentExtractedTopic(CompliancePipelineProperties props) {
        return TopicBuilder.name(props.getKafka().getTopics().getDocumentExtracted()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic needsLlmReviewTopic(CompliancePipelineProperties props) {
        return TopicBuilder.name(props.getKafka().getTopics().getNeedsLlmReview()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic documentFinalizedTopic(CompliancePipelineProperties props) {
        return TopicBuilder.name(props.getKafka().getTopics().getDocumentFinalized()).partitions(3).replicas(1).build();
    }
}
