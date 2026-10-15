package com.evision.releasemgmt.adapters.rag;

import com.evision.releasemgmt.domain.assistant.DocRef;
import com.evision.releasemgmt.domain.assistant.IndexCollection;
import com.evision.releasemgmt.domain.assistant.IndexDocument;
import com.evision.releasemgmt.domain.assistant.RagServiceException;
import com.evision.releasemgmt.ports.RagIndexPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class RagIndexAdapter implements RagIndexPort {

    private static final String COLLECTION_URI = "/v1/collections/{collection}";
    private static final String DOCUMENT_URI = "/v1/collections/{collection}/documents/{id}";

    private final RestClient client;
    private final String embeddingModel;

    public RagIndexAdapter(@Qualifier("ragIndexRestClient") RestClient client,
                           @Value("${app.assistant.embedding-model}") String embeddingModel) {
        this.client = client;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void ensureCollection(IndexCollection collection) {
        call("ensureCollection", collection.wireName(), "-", () -> client.put()
                .uri(COLLECTION_URI, collection.wireName())
                .body(new CollectionBody(embeddingModel))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void put(IndexDocument document) {
        call("put", document.collection().wireName(), document.id(), () -> client.put()
                .uri(DOCUMENT_URI, document.collection().wireName(), document.id())
                .body(new DocumentBody(document.title(), document.text(), document.metadata()))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void delete(DocRef ref) {
        try {
            call("delete", ref.collection().wireName(), ref.id(), () -> client.delete()
                    .uri(DOCUMENT_URI, ref.collection().wireName(), ref.id())
                    .retrieve()
                    .toBodilessEntity());
        } catch (RagServiceException e) {
            if (e.status() != HttpStatus.NOT_FOUND.value()) {
                throw e;
            }
        }
    }

    private void call(String operation, String collection, String id, Runnable request) {
        try {
            request.run();
        } catch (RestClientResponseException e) {
            throw new RagServiceException(message(operation, collection, id), e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new RagServiceException(message(operation, collection, id), 0);
        }
    }

    private static String message(String operation, String collection, String id) {
        return "rag " + operation + " failed collection=" + collection + " id=" + id;
    }

    private record CollectionBody(String embeddingModel) {
    }

    private record DocumentBody(String title, String text, Map<String, Object> metadata) {
    }
}
