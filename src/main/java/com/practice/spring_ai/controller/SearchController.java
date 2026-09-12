package com.practice.spring_ai.controller;

import com.practice.spring_ai.dto.SearchResult;
import com.practice.spring_ai.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @PostMapping
    public List<SearchResult> search(@RequestBody BasicPrompt query) {
        return searchService.search(query.getQuestion());
    }
}
