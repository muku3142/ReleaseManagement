package com.evision.ragservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evision.ragservice.domain.CollectionModelConflictException;
import com.evision.ragservice.domain.CollectionNotFoundException;
import com.evision.ragservice.domain.DocumentCollection;
import com.evision.ragservice.domain.EmbeddingModelNotAllowedException;
import com.evision.ragservice.ports.CollectionStore;
import com.evision.ragservice.ports.DocumentStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CollectionServiceTest {

    private static final UUID CLIENT = UUID.randomUUID();
    private static final String NAME = "docs";

    private final CollectionStore store = mock(CollectionStore.class);
    private final CollectionService service = new CollectionService(store, mock(DocumentStore.class),
            "nomic-embed-text", List.of("nomic-embed-text", "alt-model"));

    private static DocumentCollection collection(String model) {
        return new DocumentCollection(UUID.randomUUID(), CLIENT, NAME, model, Instant.now());
    }

    @Test
    void existingCollectionWithTheSameModelIsIdempotent() {
        when(store.find(CLIENT, NAME)).thenReturn(Optional.of(collection("nomic-embed-text")));

        assertThat(service.put(CLIENT, NAME, null).created()).isFalse();
    }

    @Test
    void existingCollectionWithADifferentModelConflicts() {
        when(store.find(CLIENT, NAME)).thenReturn(Optional.of(collection("nomic-embed-text")));

        assertThatThrownBy(() -> service.put(CLIENT, NAME, "alt-model"))
                .isInstanceOf(CollectionModelConflictException.class);
    }

    @Test
    void lostCreateRaceFallsBackToTheWinnersRow() {
        when(store.find(CLIENT, NAME)).thenReturn(Optional.empty(), Optional.of(collection("nomic-embed-text")));
        when(store.create(CLIENT, NAME, "nomic-embed-text")).thenReturn(Optional.empty());

        assertThat(service.put(CLIENT, NAME, null).created()).isFalse();
    }

    @Test
    void requireAllDeduplicatesNamesKeepingFirstSeenOrder() {
        DocumentCollection a = new DocumentCollection(UUID.randomUUID(), CLIENT, "a", "nomic-embed-text", Instant.now());
        DocumentCollection b = new DocumentCollection(UUID.randomUUID(), CLIENT, "b", "nomic-embed-text", Instant.now());
        when(store.find(CLIENT, "a")).thenReturn(Optional.of(a));
        when(store.find(CLIENT, "b")).thenReturn(Optional.of(b));

        assertThat(service.requireAll(CLIENT, List.of("a", "b", "a"))).containsExactly(a, b);
        verify(store, times(1)).find(CLIENT, "a");
    }

    @Test
    void requireAllTreatsAForeignNameLikeAnUnknownOne() {
        when(store.find(CLIENT, "a")).thenReturn(Optional.of(collection("nomic-embed-text")));
        when(store.find(CLIENT, "foreign")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireAll(CLIENT, List.of("a", "foreign")))
                .isInstanceOf(CollectionNotFoundException.class)
                .hasMessage("Collection not found: foreign");
    }

    @Test
    void unknownModelIsRejected() {
        assertThatThrownBy(() -> service.put(CLIENT, NAME, "other-model"))
                .isInstanceOf(EmbeddingModelNotAllowedException.class);
    }
}
