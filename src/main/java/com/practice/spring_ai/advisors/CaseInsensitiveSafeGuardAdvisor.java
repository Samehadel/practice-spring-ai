package com.practice.spring_ai.advisors;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

public class CaseInsensitiveSafeGuardAdvisor extends SafeGuardAdvisor {
    private final List<String> sensitiveWords;

    public CaseInsensitiveSafeGuardAdvisor(List<String> sensitiveWords) {
        super(sensitiveWords);
        this.sensitiveWords = sensitiveWords;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        boolean containsSensitiveWord = !CollectionUtils.isEmpty(this.sensitiveWords)
                && this.sensitiveWords.stream()
                        .anyMatch(word -> chatClientRequest.prompt().getContents().toLowerCase().contains(word.toLowerCase()));

        return containsSensitiveWord ? this.createFailureResponse(chatClientRequest) : callAdvisorChain.nextCall(chatClientRequest);
    }

    private ChatClientResponse createFailureResponse(ChatClientRequest chatClientRequest) {
        return ChatClientResponse.builder()
                .chatResponse(ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage("This is a harmful message")))).build())
                .context(Map.copyOf(chatClientRequest.context())).build();
    }
}
