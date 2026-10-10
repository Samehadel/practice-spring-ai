package com.practice.spring_ai.evaluation;

import com.practice.spring_ai.config.OllamaChatClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live LLM evaluation exercise, enabled with -Devaluation.enabled=true.
 * Reuses the application's configured Ollama client without loading unrelated services.
 */
@SpringBootTest(classes = OllamaChatClientConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ImportAutoConfiguration({OllamaApiAutoConfiguration.class, OllamaChatAutoConfiguration.class,
        ToolCallingAutoConfiguration.class})
@EnabledIfSystemProperty(named = "evaluation.enabled", matches = "true")
class ReturnPolicyEvaluationTests {

    private static final String RETURN_POLICY = """
            Customers may return unused items within 30 days of delivery.
            A receipt is required. Sale items are non-refundable.
            Damaged items must be reported within 48 hours of delivery.
            """;

    private RelevancyEvaluator relevancyEvaluator;
    private FactCheckingEvaluator factCheckingEvaluator;

    @Autowired
    private ChatModel chatModel;

    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        OllamaChatOptions.Builder chatOptions = OllamaChatOptions.builder()
                .temperature(0.0)
                .numPredict(350)
                .disableThinking();
        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultOptions(chatOptions);
        this.chatClient = chatClientBuilder.build();
        relevancyEvaluator = new RelevancyEvaluator(chatClientBuilder);
        factCheckingEvaluator = FactCheckingEvaluator.builder(chatClientBuilder).build();
    }

    @Test
    @Timeout(30)
    void shouldAcceptRelevantReturnPolicyAnswer() {
        // Arrange: ask the application's configured client to generate an answer.
        String question = "Can I return an unused item delivered 20 days ago if I have the receipt?";
        String answer = chatClient.prompt()
                .system("Answer using only the supplied policy. Mention applicable exceptions. "
                        + "If the policy does not specify something, say so. Policy:\n" + RETURN_POLICY)
                .user(question)
                .call()
                .content();
        assertThat(answer).as("The live Ollama call should return an answer").isNotBlank();

        EvaluationRequest request = new EvaluationRequest(
                question,
                List.of(new Document(RETURN_POLICY)),
                answer);

        // Act: the evaluator calls Ollama to judge the supplied answer.
        EvaluationResponse result = relevancyEvaluator.evaluate(request);

        // Assert: check quality instead of comparing exact answer text.
        assertThat(result.isPass())
                .as("The judge should accept the generated answer: %s", answer)
                .isTrue();
    }

    @Test
    @Timeout(30)
    void shouldAcceptIdentifyFalseFactForReturnPolicyAnswer() {
        // Arrange: ask the application's configured client to generate an answer.
        String question = "Can I return an unused item delivered 20 days ago if it was from sales items?";
        String answer = chatClient.prompt()
                .system("Answer using only the supplied policy. Mention applicable exceptions. "
                        + "If the policy does not specify something, say so. Policy:\n" + RETURN_POLICY)
                .user(question)
                .call()
                .content();
        assertThat(answer).as("The live Ollama call should return an answer").isNotBlank();

        EvaluationRequest request = new EvaluationRequest(
                question,
                List.of(new Document(RETURN_POLICY)),
                answer);

        // Act: the evaluator calls Ollama to judge the supplied answer.
        EvaluationResponse result = factCheckingEvaluator.evaluate(request);

        // Assert: check quality instead of comparing exact answer text.
        assertThat(result.isPass())
                .as("The judge should accept the generated answer: %s", answer)
                .isTrue();
    }

    // TODO: add FactCheckingEvaluator for an answer that contradicts the policy.
    // TODO: repeat generation and evaluation five times and report the pass rate.
    // TODO: add a completeness rubric and deterministic length checks.

}
