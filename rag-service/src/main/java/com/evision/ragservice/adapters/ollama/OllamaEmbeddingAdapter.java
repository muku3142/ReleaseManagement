package com.evision.ragservice.adapters.ollama;

import com.evision.ragservice.domain.EmbeddingPurpose;
import com.evision.ragservice.domain.EmbeddingRejectedException;
import com.evision.ragservice.domain.EmbeddingUnavailableException;
import com.evision.ragservice.ports.EmbeddingPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private final RestClient embedClient;
    private final RestClient healthClient;
    private final String baseUrl;
    private final int batchSize;
    private final String documentPrefix;
    private final String queryPrefix;

    public OllamaEmbeddingAdapter(RestClient ollamaRestClient,
                                  @Value("${rag.ollama.base-url}") String baseUrl,
                                  @Value("${rag.ollama.batch-size}") int batchSize,
                                  @Value("${rag.ollama.health-timeout}") Duration healthTimeout,
                                  @Value("${rag.ollama.document-prefix}") String documentPrefix,
                                  @Value("${rag.ollama.query-prefix}") String queryPrefix) {
        this.embedClient = ollamaRestClient;
        this.baseUrl = baseUrl;
        this.batchSize = batchSize;
        this.documentPrefix = documentPrefix;
        this.queryPrefix = queryPrefix;
        JdkClientHttpRequestFactory healthFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(healthTimeout).build());
        healthFactory.setReadTimeout(healthTimeout);
        this.healthClient = RestClient.builder().requestFactory(healthFactory).build();
    }

    @Override
    public List<float[]> embed(String model, EmbeddingPurpose purpose, List<String> inputs) {
        String prefix = purpose == EmbeddingPurpose.DOCUMENT ? documentPrefix : queryPrefix;
        List<String> prefixed = inputs.stream().map(input -> prefix + input).toList();
        List<float[]> vectors = new ArrayList<>(prefixed.size());
        for (int from = 0; from < prefixed.size(); from += batchSize) {
            List<String> batch = prefixed.subList(from, Math.min(from + batchSize, prefixed.size()));
            vectors.addAll(embedBatch(model, batch));
        }
        return vectors;
    }

    @Override
    public boolean isAvailable() {
        try {
            healthClient.get().uri(baseUrl + "/api/version").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    private List<float[]> embedBatch(String model, List<String> batch) {
        EmbedResponse response;
        try {
            response = embedClient.post()
                    .uri(baseUrl + "/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmbedRequest(model, batch, false))
                    .retrieve()
                    .onStatus(status -> status.value() == 400, (req, res) -> {
                        throw new EmbeddingRejectedException("Embedding model rejected the input");
                    })
                    .body(EmbedResponse.class);
        } catch (RestClientException e) {
            throw translate(e);
        }
        return toVectors(response, batch.size());
    }

    private static List<float[]> toVectors(EmbedResponse response, int expectedCount) {
        if (response == null || response.embeddings() == null || response.embeddings().size() != expectedCount) {
            throw new EmbeddingUnavailableException("Unexpected embedding response shape");
        }
        List<float[]> vectors = new ArrayList<>(expectedCount);
        for (List<Float> embedding : response.embeddings()) {
            if (embedding == null || embedding.size() != DIMENSIONS) {
                throw new EmbeddingUnavailableException("Unexpected embedding response shape");
            }
            float[] vector = new float[DIMENSIONS];
            for (int i = 0; i < DIMENSIONS; i++) {
                vector[i] = embedding.get(i);
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private static RuntimeException translate(RestClientException e) {
        return switch (e) {
            case HttpClientErrorException.NotFound notFound ->
                    new EmbeddingUnavailableException("Embedding model not available", e);
            case HttpServerErrorException server ->
                    new EmbeddingUnavailableException("Embedding server error", e);
            case ResourceAccessException access ->
                    new EmbeddingUnavailableException("Embedding server unreachable", e);
            default -> new EmbeddingUnavailableException("Embedding call failed", e);
        };
    }

    private record EmbedRequest(String model, List<String> input, boolean truncate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbedResponse(List<List<Float>> embeddings) {
    }
}
