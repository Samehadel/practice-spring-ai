package com.practice.spring_ai.service;

import com.practice.spring_ai.dto.ChainResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiChainService {
    private final ChatClient ollamaChatClient;

    public ChainResponse generateChallengeAndSolution (String domain) {
        if (domain == null || domain.isEmpty()) {
            throw new IllegalArgumentException("Domain cannot be null or empty");
        }

        // Call - 1 - Generate challenge
        final String challenge = ollamaChatClient.prompt()
                .user(u -> u.text("""
                        Identify one specific, realistic challenge or pain-point in the \
                        domain of {topic} that could plausibly be solved using AI agents \
                        (autonomous, tool-using LLM agents). Respond with 2-4 sentences \
                        describing only the problem. Do not propose a solution.
                        """)
                        .param("topic", domain))
                .call()
                .content();

        if (challenge == null || challenge.isEmpty()) {
            throw new RuntimeException("Failed to generate challenge");
        }

        // Call - 2 - Generate solution
        final String solution = ollamaChatClient.prompt()
                .user(u -> u.text("""
                        Here is a challenge/pain-point:
 
                        {challenge}
 
                        Propose a concrete solution to this challenge using AI agents. Cover:
                        1. What the agent(s) would do
                        2. What tools/integrations they would need
                        3. The expected outcome/benefit
 
                        Keep it practical and specific, 4-6 sentences.
                        """)
                        .param("challenge", challenge))
                .call()
                .content();

        return new ChainResponse(domain, challenge, solution);
    }
}