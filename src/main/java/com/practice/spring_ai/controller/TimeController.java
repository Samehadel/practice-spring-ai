package com.practice.spring_ai.controller;

import com.practice.spring_ai.tools.TimeTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@RestController
@RequestMapping("/local-time")
@RequiredArgsConstructor
public class TimeController {

    private final ChatClient ollamaChatClient;
    private final TimeTools timeTools;

    @GetMapping
    public String currentTime(@RequestHeader String username, @RequestBody String userQuestion) {
        return ollamaChatClient
                .prompt()
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, username))
                .tools(timeTools)
                .user(userQuestion)
                .call()
                .content();
    }
}
