package com.practice.spring_ai.dto;

import lombok.Data;

@Data
public class BasicPrompt {
    private String customerName;
    private String product;
    private String question;
}
