package com.practice.spring_ai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatClient openaiChatClient;
    private final ChatClient ollamaChatClient;

    @PostMapping("/openai")
    public String chatOpenai(@RequestBody String userAsk) {
        return openaiChatClient
                .prompt()
                .user(userAsk)
                .call()
                .content();
    }

    @PostMapping("/ollama")
    public String chatOllama(@RequestBody String userAsk) {
        return ollamaChatClient
                .prompt()
                .user(userAsk)
                .call()
                .content();
    }
}
