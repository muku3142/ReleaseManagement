package com.evision.releasemgmt.application;

import com.evision.releasemgmt.ports.ActorDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActorResolutionServiceTest {

    @Mock
    private ActorDirectory directory;

    private ActorResolutionService service;

    @BeforeEach
    void setUp() {
        service = new ActorResolutionService(directory);
    }

    @Test
    void resolvesAKnownAccountIdToItsEmail() {
        when(directory.findEmailsByAccountId(any()))
                .thenReturn(Map.of("557058:aaa", "rm@acme.com"));

        ResolvedActors actors = service.resolve("557058:aaa");

        assertThat(actors.display("557058:aaa")).isEqualTo("rm@acme.com");
    }

    @Test
    void unknownAccountIdDisplaysAsItself() {
        when(directory.findEmailsByAccountId(any())).thenReturn(Map.of());

        ResolvedActors actors = service.resolve("557058:gone");

        String display = actors.display("557058:gone");
        assertThat(display).isEqualTo("557058:gone");
        assertThat(display).isNotNull();
        assertThat(display).isNotBlank();
    }

    @Test
    void nullAccountIdDisplaysAsNullAndIsNotLookedUp() {
        ResolvedActors actors = service.resolve((String) null);

        assertThat(actors.display(null)).isNull();
        verifyNoInteractions(directory);
    }

    @Test
    void noAccountIdsIssuesNoQueryAtAll() {
        service.resolve();

        verifyNoInteractions(directory);
    }

    @Test
    void duplicateAccountIdsCollapseIntoOneLookupOfDistinctIds() {
        when(directory.findEmailsByAccountId(any())).thenReturn(Map.of());

        service.resolve("557058:aaa", "557058:aaa", "557058:bbb", null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(directory, times(1)).findEmailsByAccountId(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("557058:aaa", "557058:bbb");
    }
}
