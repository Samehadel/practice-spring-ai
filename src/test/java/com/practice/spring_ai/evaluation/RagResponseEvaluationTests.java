package com.practice.spring_ai.evaluation;

import com.practice.spring_ai.config.OllamaChatClientConfig;
import com.practice.spring_ai.config.OllamaRagChatClientConfig;
import io.qdrant.client.QdrantClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Live Ollama + Qdrant exercise; enable with -Devaluation.enabled=true. */
@SpringBootTest(classes = {OllamaChatClientConfig.class, OllamaRagChatClientConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.ai.ollama.embedding.model=embeddinggemma",
                "spring.ai.vectorstore.qdrant.host=localhost",
                "spring.ai.vectorstore.qdrant.port=6334",
                "spring.ai.vectorstore.qdrant.initialize-schema=true"
        })
@ImportAutoConfiguration({OllamaApiAutoConfiguration.class, OllamaChatAutoConfiguration.class,
        OllamaEmbeddingAutoConfiguration.class, ToolCallingAutoConfiguration.class,
        QdrantVectorStoreAutoConfiguration.class})
@Import(RagResponseEvaluationTests.LiveHttpConfiguration.class)
@EnabledIfSystemProperty(named = "evaluation.enabled", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagResponseEvaluationTests {

    private static final String COLLECTION = "rag-evaluation-" + UUID.randomUUID();

    private Document policy;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    @Qualifier("ollamaRagChatClient")
    private ChatClient ragChatClient;

    @Autowired
    @Qualifier("ollamaChatClientBuilder")
    private ChatClient.Builder judgeBuilder;

    @DynamicPropertySource
    static void isolateCollection(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.vectorstore.qdrant.collection-name", () -> COLLECTION);
    }

    @BeforeAll
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void saveDocuments() {
        policy = new Document(UUID.randomUUID().toString(), """
                Return policy for unused items: customers may return unused items within
                30 days of delivery. A receipt is required. Sale items are non-refundable.
                """, Map.of("client_id", "evaluation", "status", "active"));
        Document otherTenantPolicy = new Document(UUID.randomUUID().toString(),
                "Return policy for unused items: returns are allowed within 90 days without a receipt.",
                Map.of("client_id", "other-tenant", "status", "active"));
        vectorStore.add(List.of(policy, otherTenantPolicy));
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void shouldEvaluateAnswerUsingExactlyTheRetrievedDocuments() {
        evaluatePolicyAnswer("What is the return policy for unused items?");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void shouldEvaluateWhetherReceiptIsRequiredForReturn() {
        evaluatePolicyAnswer("Under the return policy for unused items, is a receipt required?");
    }

    private void evaluatePolicyAnswer(String question) {
        FilterExpressionBuilder filter = new FilterExpressionBuilder();
        ChatResponse response = ragChatClient.prompt()
                .user(question)
                .options(OllamaChatOptions.builder().temperature(0.0).numPredict(350).disableThinking())
                .advisors(spec -> spec.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                        filter.and(filter.eq("client_id", "evaluation"), filter.eq("status", "active")).build()))
                .call()
                .chatResponse();

        assertThat(response).isNotNull();
        String answer = response.getResult().getOutput().getText();
        assertThat(answer).isNotBlank();

        // Capture the documents used by this call. Do not run another similarity search.
        List<Document> retrievedDocuments = response.getMetadata()
                .get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        assertThat(retrievedDocuments).as("RAG must retrieve context before evaluation").isNotEmpty();
        assertThat(retrievedDocuments).extracting(Document::getId).containsExactly(policy.getId());
        assertThat(retrievedDocuments.getFirst().getText()).isEqualTo(policy.getText());

        EvaluationRequest request = new EvaluationRequest(question, retrievedDocuments, answer);
        assertThat(request.getDataList()).isSameAs(retrievedDocuments);

        // A separate non-RAG judge avoids retrieving or injecting a second set of documents.
        RelevancyEvaluator evaluator = new RelevancyEvaluator(judgeBuilder.clone()
                .defaultSystem("Follow the evaluation instructions exactly. Answer only YES or NO.")
                .defaultOptions(OllamaChatOptions.builder().temperature(0.0).numPredict(350).disableThinking()));
        EvaluationResponse verdict = evaluator.evaluate(request);
        assertThat(verdict.isPass()).as("Generated RAG answer: %s", answer).isTrue();
    }

    @AfterAll
    static void removeTestCollection(@Autowired QdrantClient client) throws Exception {
        // JUnit runs this after the class even when a test fails. The collection
        // belongs exclusively to this run, so deleting it removes all fixtures.
        client.deleteCollectionAsync(COLLECTION).get(30, TimeUnit.SECONDS);
        assertThat(client.collectionExistsAsync(COLLECTION).get(30, TimeUnit.SECONDS))
                .as("The test collection and its documents must be removed after the tests")
                .isFalse();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LiveHttpConfiguration {

        @Bean
        RestClient.Builder restClientBuilder() {
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
            factory.setReadTimeout(Duration.ofMinutes(2));
            return RestClient.builder().requestFactory(factory);
        }
    }
}
