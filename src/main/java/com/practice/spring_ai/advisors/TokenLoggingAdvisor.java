package com.practice.spring_ai.advisors;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.logging.Logger;

public class TokenLoggingAdvisor implements CallAdvisor {
    private static final Logger LOGGER = Logger.getLogger(TokenLoggingAdvisor.class.getName());
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
        LOGGER.info("Request tokens used:" + usage.getPromptTokens());
        LOGGER.info("Response tokens used: " + usage.getCompletionTokens());

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
