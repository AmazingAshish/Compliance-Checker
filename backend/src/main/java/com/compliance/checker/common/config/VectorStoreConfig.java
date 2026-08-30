package com.compliance.checker.common.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * In-memory vector store for the RAG corpus of compliance policy snippets.
 * SimpleVectorStore is intentionally chosen over pgvector here: the corpus is
 * tiny (a handful of policy documents), rebuilt fresh on every startup from
 * the text files in {@code resources/policies}, and doesn't need persistence
 * across restarts, so paying for a pgvector extension/table would be pure
 * ceremony for a project this size. Swapping to pgvector later is a one-bean
 * change since callers only depend on the {@link VectorStore} interface.
 *
 * The {@link EmbeddingModel} parameter is {@code @Lazy}: Spring AI's Google
 * GenAI embedding auto-configuration throws eagerly at bean-creation time
 * when no API key is set (it assumes Vertex AI and demands a project-id).
 * Deferring that instantiation to first real use lets the app start without
 * a key; the failure then surfaces inside PolicyVectorStoreInitializer's
 * try/catch instead of crashing the whole context.
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(@Lazy EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
