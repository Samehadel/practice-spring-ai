package com.practice.spring_ai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatClient ollamaChatClient;

    @PostMapping("/ollama")
    public String chatOllama(@RequestBody String userAsk) {
        return ollamaChatClient
                .prompt()
                .user(userAsk)
                .call()
                .content();
    }
}
