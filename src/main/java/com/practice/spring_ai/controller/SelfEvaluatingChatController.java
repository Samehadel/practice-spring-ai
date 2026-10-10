package com.practice.spring_ai.controller;

import com.practice.spring_ai.exception.InvalidLlmResponseException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/self-evaluating-chat")
public class SelfEvaluatingChatController {

    private final ChatClient ragChatClient;
    private final FactCheckingEvaluator factCheckingEvaluator;

    public SelfEvaluatingChatController(@Qualifier("ollamaRagChatClient") ChatClient ragChatClient,
                                       FactCheckingEvaluator factCheckingEvaluator) {
        this.ragChatClient = ragChatClient;
        this.factCheckingEvaluator = factCheckingEvaluator;
    }

    @PostMapping
    public String chat(@RequestBody BasicPrompt prompt, @RequestParam String clientId) {
        FilterExpressionBuilder filter = new FilterExpressionBuilder();
        ChatResponse response = ragChatClient.prompt()
                .user(prompt.getQuestion())
                .advisors(spec -> spec.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                        filter.and(filter.eq("client_id", clientId), filter.eq("status", "active")).build()))
                .call()
                .chatResponse();
        return validateAnswer(prompt.getQuestion(), response);
    }

    @Retryable(retryFor =  InvalidLlmResponseException.class, maxAttempts = 2, recover = "recoverFromInvalidLlmResponse")
    @PostMapping("incorrect")
    public String chatWithIncorrectResponse(@RequestBody BasicPrompt prompt, @RequestParam String clientId) {
        FilterExpressionBuilder filter = new FilterExpressionBuilder();
        ChatResponse response = ragChatClient.prompt()
                .user(prompt.getQuestion())
                .advisors(spec -> spec.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                        filter.and(filter.eq("client_id", clientId), filter.eq("status", "active")).build()))
                .call()
                .chatResponse();
        // Inject a fixed candidate to exercise validation/recovery deterministically.
        // A system prompt cannot guarantee that a model will output exactly Yes.
        return validateAnswer(prompt.getQuestion(), response, "Yes");
    }

    private String validateAnswer(String question, ChatResponse response) {
        if (response == null || response.getResult() == null) {
            throw new InvalidLlmResponseException();
        }
        String answer = response.getResult().getOutput().getText();
        return validateAnswer(question, response, answer);
    }

    private String validateAnswer(String question, ChatResponse response, String answer) {
        if (response == null) {
            throw new InvalidLlmResponseException();
        }
        List<Document> documents = response.getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        if (!StringUtils.hasText(answer) || documents == null || documents.isEmpty()) {
            throw new InvalidLlmResponseException();
        }
        EvaluationRequest request = new EvaluationRequest(question, documents, answer);
        if (!factCheckingEvaluator.evaluate(request).isPass()) {
            throw new InvalidLlmResponseException();
        }
        return answer;
    }

    @Recover
    public String recoverFromInvalidLlmResponse(InvalidLlmResponseException e) {
        return "I can't answer this question reliably. Please try again.";
    }
}
