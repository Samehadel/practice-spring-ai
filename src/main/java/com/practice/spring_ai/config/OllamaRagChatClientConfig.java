package com.practice.spring_ai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;


@Configuration
@RequiredArgsConstructor
public class OllamaRagChatClientConfig {

    private final ChatClient.Builder ollamaChatClientBuilder;

    @Value("classpath:/templates/prompts/systemPromptTemplate.st")
    private Resource systemPromptTemplate;

    @Bean
    public ChatClient ollamaRagChatClient(VectorStore vectorStore) {
        ChatOptions defaultChatOptions = buildDefualtChatOptions();
        return ollamaChatClientBuilder.clone()
                .defaultAdvisors(buildRetrievalAugmentationAdvisor(vectorStore))
                .defaultOptions(defaultChatOptions.mutate())
                .build();
    }

    private ChatOptions buildDefualtChatOptions() {
        return ChatOptions.builder()
                .maxTokens(4096)
                .temperature(0.8)
                .build();
    }

    private RetrievalAugmentationAdvisor buildRetrievalAugmentationAdvisor(VectorStore vectorStore) {
        VectorStoreDocumentRetriever vectorStoreDocumentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(10)
                .similarityThreshold(0.6)
                .build();

        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .promptTemplate(new PromptTemplate(systemPromptTemplate))
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(vectorStoreDocumentRetriever)
                .queryAugmenter(queryAugmenter)
                .build();
    }
}
