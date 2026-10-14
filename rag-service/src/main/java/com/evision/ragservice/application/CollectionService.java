package com.evision.ragservice.application;

import com.evision.ragservice.domain.CollectionModelConflictException;
import com.evision.ragservice.domain.CollectionNotFoundException;
import com.evision.ragservice.domain.CollectionStatus;
import com.evision.ragservice.domain.DocumentCollection;
import com.evision.ragservice.domain.EmbeddingModelNotAllowedException;
import com.evision.ragservice.ports.CollectionStore;
import com.evision.ragservice.ports.DocumentStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CollectionService {

    public record PutResult(DocumentCollection collection, boolean created) {
    }

    private final CollectionStore collections;
    private final DocumentStore documents;
    private final String defaultModel;
    private final List<String> allowedModels;

    public CollectionService(CollectionStore collections, DocumentStore documents,
                             @Value("${rag.embedding.default-model}") String defaultModel,
                             @Value("${rag.embedding.allowed-models}") List<String> allowedModels) {
        this.collections = collections;
        this.documents = documents;
        this.defaultModel = defaultModel;
        this.allowedModels = allowedModels;
    }

    public PutResult put(UUID clientId, String name, String requestedModel) {
        String model = requestedModel == null || requestedModel.isBlank() ? defaultModel : requestedModel;
        if (!allowedModels.contains(model)) {
            throw new EmbeddingModelNotAllowedException(model);
        }
        Optional<DocumentCollection> existing = collections.find(clientId, name);
        if (existing.isPresent()) {
            return sameModelOrConflict(existing.get(), model);
        }
        Optional<DocumentCollection> created = collections.create(clientId, name, model);
        if (created.isPresent()) {
            return new PutResult(created.get(), true);
        }
        DocumentCollection raced = collections.find(clientId, name).orElseThrow(() -> new CollectionNotFoundException(name));
        return sameModelOrConflict(raced, model);
    }

    public DocumentCollection require(UUID clientId, String name) {
        return collections.find(clientId, name).orElseThrow(() -> new CollectionNotFoundException(name));
    }

    public List<DocumentCollection> requireAll(UUID clientId, List<String> names) {
        return names.stream().distinct().map(name -> require(clientId, name)).toList();
    }

    public List<CollectionStatus> list(UUID clientId) {
        return collections.listStatus(clientId);
    }

    public CollectionStatus status(UUID clientId, String name) {
        return collections.findStatus(clientId, name).orElseThrow(() -> new CollectionNotFoundException(name));
    }

    // Old chunks stay searchable until each document's new embedding lands
    public int reindex(UUID clientId, String name) {
        return documents.requeueAll(require(clientId, name).id());
    }

    private static PutResult sameModelOrConflict(DocumentCollection collection, String model) {
        if (!collection.embeddingModel().equals(model)) {
            throw new CollectionModelConflictException(collection.name());
        }
        return new PutResult(collection, false);
    }
}
