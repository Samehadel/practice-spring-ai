package com.practice.spring_ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.practice.spring_ai.config.TavilyProperties;
import com.practice.spring_ai.dto.SearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final RestClient client;
    private final TavilyProperties properties;

    public List<SearchResult> search(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("Set TAVILY_API_KEY before using web search");
        }
        try {
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
                    .map(result -> new SearchResult(result.title(), result.url(), result.content()))
                    .toList();
        } catch (RestClientException exception) {
            // Do not expose provider response bodies or credentials in application errors.
            throw new SearchException("Tavily search failed; check provider availability and configuration");
        }
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
