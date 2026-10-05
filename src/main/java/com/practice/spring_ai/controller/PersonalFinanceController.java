package com.practice.spring_ai.controller;

import com.practice.spring_ai.tools.PersonalFinanceTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/personal-finance")
@RequiredArgsConstructor
public class PersonalFinanceController {

    private final ChatClient ollamaChatClient;
    private final PersonalFinanceTools personalFinanceTools;

    @PostMapping
    public String ask(@RequestBody String question) {
        return ollamaChatClient.prompt()
                .system("Answer questions about the sample account using the registered finance tools. "
                        + "The balance and expenses are hardcoded sample data as of October 6, 2026. "
                        + "Do not claim to have access to live financial data.")
                .tools(personalFinanceTools)
                .user(question)
                .call()
                .content();
    }
}
