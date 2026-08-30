package com.compliance.checker.llmreview;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads the synthetic compliance-policy corpus under resources/policies into
 * the in-memory vector store at startup, so LlmReviewService can retrieve
 * relevant policy passages (RAG) instead of relying on the model's general
 * knowledge of "compliance" - which is exactly what makes the LLM's reasoning
 * traceable back to a specific, auditable policy document.
 */
@Component
@Slf4j
public class PolicyVectorStoreInitializer {

    private static final String POLICY_LOCATION_PATTERN = "classpath:policies/*.txt";

    private final VectorStore vectorStore;

    public PolicyVectorStoreInitializer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadPolicyCorpus() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(POLICY_LOCATION_PATTERN);

            List<Document> allChunks = new ArrayList<>();
            TokenTextSplitter splitter = new TokenTextSplitter();

            for (Resource resource : resources) {
                TextReader reader = new TextReader(resource);
                reader.getCustomMetadata().put("source", resource.getFilename());
                List<Document> docs = reader.get();
                allChunks.addAll(splitter.apply(docs));
            }

            if (!allChunks.isEmpty()) {
                vectorStore.add(allChunks);
                log.info("Loaded {} policy chunks from {} files into the vector store", allChunks.size(), resources.length);
            } else {
                log.warn("No policy documents found under {}", POLICY_LOCATION_PATTERN);
            }
        } catch (Exception e) {
            // Startup embedding failure (e.g. missing/invalid GEMINI_API_KEY) must not
            // prevent the whole app from starting - the LLM review consumer will simply
            // fail per-document (marked LLM_REVIEW_FAILED) until the key is fixed.
            log.error("Failed to load policy corpus into vector store; RAG context will be empty until this is fixed", e);
        }
    }
}
