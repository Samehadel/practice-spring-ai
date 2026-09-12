package com.practice.spring_ai.advisors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

public class TokenLoggingAdvisor implements CallAdvisor {
    private static final Logger log = LoggerFactory.getLogger(TokenLoggingAdvisor.class);
    private static final int ORDER = 0;
    private static final String NAME = "TokenLoggingAdvisor";

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
        ChatResponse chatResponse = chatClientResponse.chatResponse();

        if (chatResponse == null) {
            return chatClientResponse;
        }

        Usage usage = chatResponse.getMetadata().getUsage();
        log.info("Request tokens used:{}", usage.getPromptTokens());
        log.info("Response tokens used: {}", usage.getCompletionTokens());

        return  chatClientResponse;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
