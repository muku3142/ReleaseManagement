package com.evision.ragservice.application;

import com.evision.ragservice.domain.ContentHash;
import com.evision.ragservice.domain.DocumentContent;
import com.evision.ragservice.domain.DocumentNotFoundException;
import com.evision.ragservice.domain.DocumentStatus;
import com.evision.ragservice.domain.DocumentView;
import com.evision.ragservice.domain.InvalidContentException;
import com.evision.ragservice.domain.MetadataRules;
import com.evision.ragservice.domain.PayloadTooLargeException;
import com.evision.ragservice.ports.DocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    private final CollectionService collections;
    private final DocumentStore documents;
    private final long maxTextBytes;
    private final int maxTitleChars;

    public DocumentService(CollectionService collections, DocumentStore documents,
                           @Value("${rag.limits.max-text-bytes}") long maxTextBytes,
                           @Value("${rag.limits.max-title-chars}") int maxTitleChars) {
        this.collections = collections;
        this.documents = documents;
        this.maxTextBytes = maxTextBytes;
        this.maxTitleChars = maxTitleChars;
    }

    public DocumentStatus push(UUID clientId, String collection, DocumentContent content) {
        if (content.text().getBytes(StandardCharsets.UTF_8).length > maxTextBytes) {
            throw new PayloadTooLargeException(maxTextBytes);
        }
        if (content.text().isBlank()) {
            throw new InvalidContentException("text must not be blank");
        }
        if (content.title().length() > maxTitleChars) {
            throw new InvalidContentException("title must not exceed " + maxTitleChars + " characters");
        }
        MetadataRules.rejectNul("title", content.title());
        MetadataRules.rejectNul("text", content.text());
        MetadataRules.rejectNul("url", content.url());
        MetadataRules.validateMetadata(content.metadata());
        var target = collections.require(clientId, collection);
        return documents.upsert(target.id(), content, ContentHash.of(content.title(), content.text()));
    }

    public DocumentView get(UUID clientId, String collection, String documentId) {
        return documents.find(collections.require(clientId, collection).id(), documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    public void delete(UUID clientId, String collection, String documentId) {
        if (!documents.delete(collections.require(clientId, collection).id(), documentId)) {
            throw new DocumentNotFoundException(documentId);
        }
    }
}
