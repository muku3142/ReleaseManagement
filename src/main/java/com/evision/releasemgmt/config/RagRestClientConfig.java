package com.evision.releasemgmt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class RagRestClientConfig {

    @Bean
    RestClient ragIndexRestClient(@Value("${app.assistant.base-url}") String baseUrl,
                                  @Value("${app.assistant.api-key}") String apiKey,
                                  @Value("${app.assistant.index.connect-timeout}") Duration connectTimeout,
                                  @Value("${app.assistant.index.read-timeout}") Duration readTimeout) {
        return ragClient(baseUrl, apiKey, connectTimeout, readTimeout);
    }

    // HTTP/1.1 because the JDK client's h2c upgrade attempt on plain-http bodies fails against HTTP/1.1-only servers. Redirect.NEVER is the default and kept.
    public static RestClient ragClient(String baseUrl, String apiKey, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }
}
