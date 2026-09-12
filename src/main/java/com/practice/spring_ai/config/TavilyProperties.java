package com.practice.spring_ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tavily")
public record TavilyProperties(String baseUrl, String apiKey, Duration timeout, int maxResults) {
    public TavilyProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("tavily.base-url is required");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("tavily.timeout must be positive");
        }
        if (maxResults < 1 || maxResults > 20) {
            throw new IllegalArgumentException("tavily.max-results must be between 1 and 20");
        }
    }
}
