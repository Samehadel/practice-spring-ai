package com.practice.spring_ai.controller;

import com.practice.spring_ai.dto.ChainResponse;
import com.practice.spring_ai.service.AiChainService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chain")
@RequiredArgsConstructor
public class ChainController {
    private final AiChainService aiChainService;

    @PostMapping
    public ChainResponse generateChallengeAndSolution(@RequestBody String domain) {
        return aiChainService.generateChallengeAndSolution(domain);
    }
}
