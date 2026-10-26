package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReleaseRefTest {

    private final ReleaseService service = mock(ReleaseService.class);
    private final ReleaseRef ref = new ReleaseRef(service);

    private static ReleaseSummary summary(UUID id, String name, String version) {
        return new ReleaseSummary(id, name, version, ReleaseStatus.OPEN, null, Instant.EPOCH, "a", null, null, null, null, 0);
    }

    @Test
    void resolvesExactNameVersionAndCombined() {
        UUID id = UUID.randomUUID();
        when(service.search(anyString())).thenReturn(
                List.of(summary(id, "Payments", "2.4"), summary(UUID.randomUUID(), "Payments", "2.5")));
        assertThat(ref.resolve(" payments 2.4 ")).isEqualTo(id);
        assertThat(ref.resolve("2.4")).isEqualTo(id);
    }

    @Test
    void unknownIsRejected() {
        when(service.search(anyString())).thenReturn(List.of());
        assertThatThrownBy(() -> ref.resolve("nope")).isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("no release matches");
    }

    @Test
    void unknownListsKnownReleases() {
        when(service.search("Relase")).thenReturn(List.of());
        when(service.search("")).thenReturn(List.of(summary(UUID.randomUUID(), "Release 4.3", "4.3")));
        assertThatThrownBy(() -> ref.resolve("Relase")).isInstanceOf(ToolArgumentException.class)
                .hasMessage("no release matches 'Relase'; known releases: Release 4.3 (4.3)");
    }

    @Test
    void unknownListsAtMostFiveKnownReleasesInOrder() {
        when(service.search("nope")).thenReturn(List.of());
        when(service.search("")).thenReturn(IntStream.range(0, 7)
                .mapToObj(i -> summary(UUID.randomUUID(), "R", "v" + i)).toList());
        assertThatThrownBy(() -> ref.resolve("nope")).isInstanceOf(ToolArgumentException.class)
                .hasMessage("no release matches 'nope'; known releases: R (v0), R (v1), R (v2), R (v3), R (v4)");
    }

    @Test
    void unknownWithNoReleasesAtAllHasNoSuffix() {
        when(service.search(anyString())).thenReturn(List.of());
        assertThatThrownBy(() -> ref.resolve("nope")).isInstanceOf(ToolArgumentException.class)
                .hasMessage("no release matches 'nope'");
    }

    @Test
    void unknownUuidListsKnownReleases() {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new ReleaseNotFoundException());
        when(service.search("")).thenReturn(List.of(summary(UUID.randomUUID(), "Release 4.3", "4.3")));
        assertThatThrownBy(() -> ref.resolve(id.toString())).isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("known releases: Release 4.3 (4.3)");
    }

    @Test
    void ambiguousListsAtMostFiveCandidates() {
        List<ReleaseSummary> seven = IntStream.range(0, 7)
                .mapToObj(i -> summary(UUID.randomUUID(), "Same", "v" + i)).toList();
        when(service.search(anyString())).thenReturn(seven);
        assertThatThrownBy(() -> ref.resolve("same")).isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("candidates")
                .satisfies(e -> assertThat(e.getMessage().split("candidates: ")[1].split(", ")).hasSize(5));
    }

    @Test
    void blankAndOverlongAreRejected() {
        assertThatThrownBy(() -> ref.resolve("  ")).isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> ref.resolve(null)).isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> ref.resolve("x".repeat(101))).isInstanceOf(ToolArgumentException.class);
    }
}
