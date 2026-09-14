package com.practice.spring_ai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("web-search")
@RequiredArgsConstructor
public class WebSearchChatController {
    private final ChatClient ollamaWebSearchChatClient;

    @PostMapping
    public String chat(@RequestBody BasicPrompt prompt) {
        return ollamaWebSearchChatClient.prompt()
                .user(prompt.getQuestion())
                .call()
                .content();
    }
}
