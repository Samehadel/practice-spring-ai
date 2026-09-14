package com.practice.spring_ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.practice.spring_ai.config.TavilyProperties;
import com.practice.spring_ai.dto.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Service
@Slf4j
public class SearchService {
    private final RestClient client;
    private final TavilyProperties properties;

    public SearchService(@Qualifier("tavilyRestClient") RestClient client, TavilyProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public List<SearchResult> search(String query) {
        long started = System.nanoTime();
        try {
            for (int attempt = 0; ; attempt++) {
                try {
                    var results = executeSearch(query);
                    log.info("Web search completed: results={}, attempts={}, durationMs={}",
                            results.size(), attempt + 1, (System.nanoTime() - started) / 1_000_000);
                    return results;
                } catch (RestClientException exception) {
                    boolean retryable = exception instanceof ResourceAccessException
                            || exception instanceof RestClientResponseException response
                            && response.getStatusCode().is5xxServerError();
                    if (!retryable || attempt >= 1) {
                        throw new SearchException("Tavily search failed; check provider availability and configuration");
                    }
                    log.warn("Retrying web search after transient provider failure");
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Web search failed: type={}, durationMs={}", exception.getClass().getSimpleName(),
                    (System.nanoTime() - started) / 1_000_000);
            throw exception;
        }
    }

    private List<SearchResult> executeSearch(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("Set TAVILY_API_KEY before using web search");
        }
        var response = client.post().uri("/search")
                .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SearchRequest(query.trim(), "basic", properties.maxResults(), "general"))
                .retrieve()
                .body(SearchResponse.class);
        if (response == null || response.results() == null) {
            throw new SearchException("Tavily returned an invalid search response");
        }
        return response.results().stream()
                .limit(properties.maxResults())
                .map(result -> new SearchResult(truncate(result.title(), 300), result.url(), truncate(result.content(), 4000)))
                .toList();
    }

    private static String truncate(String text, int limit) {
        return text == null ? "" : text.substring(0, Math.min(text.length(), limit));
    }

    private record SearchRequest(String query,
                                 @JsonProperty("search_depth") String searchDepth,
                                 @JsonProperty("max_results") int maxResults,
                                 String topic) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(List<TavilyResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilyResult(String title, String url, String content) {
    }

    public static class SearchException extends RuntimeException {
        public SearchException(String message) {
            super(message);
        }
    }
}
