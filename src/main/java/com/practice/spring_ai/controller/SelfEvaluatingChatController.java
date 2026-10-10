package com.practice.spring_ai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/self-evaluating-chat")
@RequiredArgsConstructor
public class SelfEvaluatingChatController {

    private final FactCheckingEvaluator factCheckingEvaluator;


}
