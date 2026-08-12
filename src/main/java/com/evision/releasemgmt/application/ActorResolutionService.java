package com.evision.releasemgmt.application;

import com.evision.releasemgmt.ports.ActorDirectory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ActorResolutionService {

    private final ActorDirectory directory;

    public ActorResolutionService(ActorDirectory directory) {
        this.directory = directory;
    }

    /**
     * D-05.3-05: one call for a whole response, not one per row. Varargs because seven of the nine
     * call sites resolve one or two ids inline; a list site passes {@code toArray(String[]::new)},
     * which — unlike {@code List.of} — tolerates the null a release that was never frozen carries.
     */
    public ResolvedActors resolve(String... accountIds) {
        Set<String> distinct = Arrays.stream(accountIds)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return new ResolvedActors(distinct.isEmpty() ? Map.of()
                : directory.findEmailsByAccountId(distinct));
    }
}
