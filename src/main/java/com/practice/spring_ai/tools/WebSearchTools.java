package com.practice.spring_ai.tools;

import com.practice.spring_ai.dto.SearchResult;
import com.practice.spring_ai.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class WebSearchTools {
    private final SearchService searchService;

    public WebSearchTools(SearchService searchService) {
        this.searchService = searchService;
    }

    @Tool(
            name = "webSearch",
            description = "Search the web for current facts or information requiring external sources. "
            + "Returns source titles, URLs and relevant text. Use source URLs for citations."
    )
    public SearchOutcome webSearch(String query) {
        try {
            log.info("Web search query: {}", query);
            return new SearchOutcome(searchService.search(query), null);
        } catch (SearchService.SearchException | IllegalStateException | IllegalArgumentException exception) {
            return new SearchOutcome(List.of(), "Web search is unavailable or the query is invalid. "
                    + "Do not invent results or citations; explain that current information could not be verified.");
        }
    }

    public record SearchOutcome(List<SearchResult> results, String error) { }
}
