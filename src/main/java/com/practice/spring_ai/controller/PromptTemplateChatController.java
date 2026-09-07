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
@RequestMapping("/prompt-template")
@RequiredArgsConstructor
public class PromptTemplateChatController {
    private final ChatClient ollamaChatClient;

    @Value("classpath:/templates/prompts/BasicPromptTemplate.st")
    private Resource basicPromptTemplate;

    @PostMapping("/basic")
    public String chatBasicPromptTemplate(@RequestBody BasicPrompt userPrompt) {
        return ollamaChatClient.prompt()
                .system("""
                        You are professional customer service assistant. Your role is to answer customers
                        inquires in a high quality and professional manner.
                        """)
                .user(
                        spec -> spec.text(basicPromptTemplate)
                                .param(
                                        "customerName", userPrompt.getCustomerName()
                                )
                                .param(
                                        "product", userPrompt.getProduct()
                                )
                                .param(
                                        "question", userPrompt.getQuestion()
                                )
                )
                .call()
                .content();

    }
}
