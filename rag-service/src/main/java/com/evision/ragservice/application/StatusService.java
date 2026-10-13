package com.evision.ragservice.application;

import com.evision.ragservice.domain.QueueDepth;
import com.evision.ragservice.ports.DocumentStore;
import com.evision.ragservice.ports.EmbeddingPort;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StatusService {

    public record ServiceStatus(boolean ollamaAvailable, QueueDepth queue) {
    }

    private final EmbeddingPort embeddings;
    private final DocumentStore documents;

    public StatusService(EmbeddingPort embeddings, DocumentStore documents) {
        this.embeddings = embeddings;
        this.documents = documents;
    }

    public ServiceStatus status(UUID clientId) {
        return new ServiceStatus(embeddings.isAvailable(), documents.queueDepth(clientId));
    }
}
