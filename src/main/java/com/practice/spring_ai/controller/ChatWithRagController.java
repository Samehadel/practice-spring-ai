package com.practice.spring_ai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class ChatWithRagController {

    private final ChatClient ollamaRagChatClient;


    @PostMapping("/report")
    public String chatWithDocuments(@RequestBody BasicPrompt prompt, @RequestParam(required = false) String conversationsId) {
        if (conversationsId == null) {
            conversationsId = "default";
        }
        final String finalConversationsId = conversationsId;

        return ollamaRagChatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, finalConversationsId))
                .user(prompt.getQuestion())
                .call()
                .content();
    }
}
