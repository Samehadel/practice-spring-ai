package com.practice.spring_ai.config;

import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OllamaHttpConfig {

    private static final Duration READ_TIMEOUT = Duration.ofMinutes(2);

    @Bean
    public OllamaApi ollamaApi(OllamaConnectionDetails connectionDetails,
                               ObjectProvider<RestClient.Builder> restClientBuilders,
                               ObjectProvider<WebClient.Builder> webClientBuilders) {
        ReactorClientHttpRequestFactory factory = new ReactorClientHttpRequestFactory();
        factory.setReadTimeout(READ_TIMEOUT);
        return OllamaApi.builder()
                .baseUrl(connectionDetails.getBaseUrl())
                .restClientBuilder(restClientBuilders.getIfAvailable(RestClient::builder).clone()
                        .requestFactory(factory))
                .webClientBuilder(webClientBuilders.getIfAvailable(WebClient::builder).clone()
                        .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                                .responseTimeout(READ_TIMEOUT))))
                .build();
    }
}
