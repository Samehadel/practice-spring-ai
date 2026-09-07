package com.practice.spring_ai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@RestController
@RequestMapping("/chat-memory")
@RequiredArgsConstructor
public class ChatWithMemoryController {
    private final ChatClient ollamaInMemoryChatClient;

    @Value("classpath:/templates/prompts/BasicPromptTemplate.st")
    private Resource basicPromptTemplate;

    @PostMapping
    public String chatBasicPromptTemplate(@RequestParam String prompt, @RequestParam(required = false) String conversationsId) {
        if (conversationsId == null) {
            conversationsId = "default";
        }
        final String finalConversationsId = conversationsId;
        return ollamaInMemoryChatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, finalConversationsId))
                .user(prompt)
                .call()
                .content();

    }
}
