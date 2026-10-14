package com.evision.ragservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class OllamaRestClientConfig {

    @Bean
    RestClient ollamaRestClient(@Value("${rag.ollama.connect-timeout}") Duration connectTimeout,
                                @Value("${rag.ollama.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Bean
    RestClient ollamaStreamClient(ChatProperties chat, @Value("${rag.ollama.connect-timeout}") Duration connectTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        // Spring 7 bounds the whole exchange, body included, until the stream is closed; the deadline watchdog fires first
        requestFactory.setReadTimeout(chat.deadline().plusSeconds(5));
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
