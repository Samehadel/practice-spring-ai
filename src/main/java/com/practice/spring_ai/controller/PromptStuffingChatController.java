package com.practice.spring_ai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/prompt-stuffing")
@RequiredArgsConstructor
public class PromptStuffingChatController {
    private final ChatClient ollamaChatClient;

    @Value("classpath:/templates/prompts/StuffingPromptTemplate.st")
    private Resource promptStuffing;

    @PostMapping
    public String chatPromptStuffing(@RequestBody BasicPrompt userPrompt) {
        return ollamaChatClient.prompt()
                .system(promptStuffing)
                .user(userPrompt.getQuestion())
                .call()
                .content();

    }
}
