package com.evision.releasemgmt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AdoRestClientConfig {

    // Redirects are deliberately not followed: a bad ADO PAT is answered with a 302 to a sign-in page
    // (captured 2026-10-12), so following it would turn a rejection into a 200 HTML page.
    // HttpClient's default is Redirect.NEVER.
    @Bean
    RestClient adoRestClient(@Value("${app.ado.connect-timeout}") Duration connectTimeout,
                             @Value("${app.ado.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
