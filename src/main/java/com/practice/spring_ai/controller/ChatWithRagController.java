package com.practice.spring_ai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class ChatWithRagController {

    private final ChatClient ollamaRagChatClient;
    private final VectorStore vectorStore;

    @Value("classpath:/templates/prompts/systemPromptRandomDataTemplate.st")
    private Resource systemPromptTemplate;

    @Value("classpath:/templates/prompts/systemPromptFOWDataTemplate.st")
    private Resource systemPromptFOWDataTemplate;

    @GetMapping("/random")
    public String chatWithRandomDocuments(@RequestParam String prompt, @RequestParam(required = false) String conversationsId) {
        if (conversationsId == null) {
            conversationsId = "default";
        }
        final String finalConversationsId = conversationsId;
        final SearchRequest searchRequest = SearchRequest.builder()
                .query(prompt)
                .topK(10)
                .similarityThreshold(0.5)
                .build();

        final String relatedDocumentsContent = vectorStore.similaritySearch(searchRequest)
                .stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        return ollamaRagChatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, finalConversationsId))
                .system(
                        promptSystemSpec -> promptSystemSpec.text(systemPromptTemplate)
                                .param("documents", relatedDocumentsContent)
                        )
                .user(prompt)
                .call()
                .content();
    }

    @GetMapping("/report")
    public String chatWithDocuments(@RequestParam String prompt, @RequestParam(required = false) String conversationsId) {
        if (conversationsId == null) {
            conversationsId = "default";
        }
        final String finalConversationsId = conversationsId;
        final SearchRequest searchRequest = SearchRequest.builder()
                .query(prompt)
                .topK(10)
                .similarityThreshold(0.5)
                .build();

        final String relatedDocumentsContent = vectorStore.similaritySearch(searchRequest)
                .stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        return ollamaRagChatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, finalConversationsId))
                .system(
                        promptSystemSpec -> promptSystemSpec.text(systemPromptFOWDataTemplate)
                                .param("documents", relatedDocumentsContent)
                        )
                .user(prompt)
                .call()
                .content();
    }
}
