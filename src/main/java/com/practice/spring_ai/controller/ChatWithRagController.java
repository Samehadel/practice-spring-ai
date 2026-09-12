package com.practice.spring_ai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.web.bind.annotation.*;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@RestController
@RequestMapping("/rag")
public class ChatWithRagController {

    private final ChatClient ollamaRagChatClient;

    public ChatWithRagController(ChatClient ollamaRagChatClient) {
        this.ollamaRagChatClient = ollamaRagChatClient;
    }

    @PostMapping
    public String chatWithDocuments(@RequestBody BasicPrompt prompt,
                                    @RequestParam String clientId) {
        Filter.Expression tenantFilter = buildTenantFilter(clientId);

        return ollamaRagChatClient.prompt()
                .advisors(advisorSpec -> advisorSpec
                        .param(CONVERSATION_ID, clientId)
                        .param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, tenantFilter))
                .user(prompt.getQuestion())
                .call()
                .content();
    }

    private Filter.Expression buildTenantFilter(final String clientId) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        return builder.and(
                builder.eq("client_id", clientId),
                builder.eq("status", "active")
        ).build();
    }
}
