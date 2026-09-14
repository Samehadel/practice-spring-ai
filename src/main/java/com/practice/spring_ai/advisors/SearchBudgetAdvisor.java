package com.practice.spring_ai.advisors;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicInteger;

/** Allocates a separate budget for each request, including each streaming subscription. */
public class SearchBudgetAdvisor implements CallAdvisor, StreamAdvisor {
    private final int maxCalls;

    public SearchBudgetAdvisor(int maxCalls) {
        if (maxCalls < 1) throw new IllegalArgumentException("Search budget must be positive");
        this.maxCalls = maxCalls;
    }

    private ChatClientRequest withBudget(ChatClientRequest request) {
        var copy = request.copy();
        if (!(copy.prompt().getOptions() instanceof ToolCallingChatOptions options)) {
            throw new IllegalStateException("Web search requires tool calling options");
        }
        var calls = new AtomicInteger();
        var budgetedOptions = options.mutate().toolCallbacks(options.getToolCallbacks().stream().map(callback -> {
            if (!callback.getToolDefinition().name().equals("webSearch")) return callback;

            return new ToolCallback() {
                public ToolDefinition getToolDefinition() { return callback.getToolDefinition(); }
                public ToolMetadata getToolMetadata() { return callback.getToolMetadata(); }
                public String call(String input) { return call(input, null); }
                public String call(String input, ToolContext context) {
                    // Throw outside the annotated method to stop the model's tool loop.
                    if (calls.incrementAndGet() > maxCalls) {
                        throw new IllegalStateException("Web search budget exhausted for this request");
                    }
                    return callback.call(input, context);
                }
            };
        }).toList()).build();
        return copy.mutate()
                .prompt(copy.prompt().mutate().chatOptions(budgetedOptions).build())
                .build();
    }

    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(withBudget(request));
    }

    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return Flux.defer(() -> chain.nextStream(withBudget(request)));
    }

    public String getName() { return "SearchBudgetAdvisor"; }
    // Allocate the budget once, before Spring AI 2's tool-calling loop begins.
    public int getOrder() { return ToolCallingAdvisor.DEFAULT_ORDER - 1; }
}
