package com.evision.releasemgmt.domain.portfolio;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// D-16-09..11: newest by current frozen date (a refrozen release counts from its new frozen_at), never by version.
public final class FrozenReleaseOrder {

    private static final Comparator<FrozenRelease> OLDEST_FIRST =
            Comparator.comparing(FrozenRelease::frozenAt).thenComparing(FrozenRelease::id);

    private final List<FrozenRelease> oldestFirst;

    private FrozenReleaseOrder(List<FrozenRelease> oldestFirst) {
        this.oldestFirst = oldestFirst;
    }

    public static FrozenReleaseOrder of(Collection<FrozenRelease> frozen) {
        return new FrozenReleaseOrder(frozen.stream().sorted(OLDEST_FIRST).toList());
    }

    public Optional<FrozenRelease> newest() {
        return oldestFirst.isEmpty() ? Optional.empty() : Optional.of(oldestFirst.getLast());
    }

    public boolean isEmpty() {
        return oldestFirst.isEmpty();
    }

    public int releasesBehind(UUID runningReleaseId) {
        int index = indexOf(runningReleaseId);
        return index < 0 ? 0 : oldestFirst.size() - 1 - index;
    }

    private int indexOf(UUID releaseId) {
        for (int i = 0; i < oldestFirst.size(); i++) {
            if (oldestFirst.get(i).id().equals(releaseId)) {
                return i;
            }
        }
        return -1;
    }
}
